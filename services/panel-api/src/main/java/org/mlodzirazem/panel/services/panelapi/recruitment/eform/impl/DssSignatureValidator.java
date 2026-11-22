/*
 * Copyright (C) 2025 Stowarzyszenie Młodzi Razem
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mlodzirazem.panel.services.panelapi.recruitment.eform.impl;

import eu.europa.esig.dss.enumerations.Indication;
import eu.europa.esig.dss.enumerations.SubIndication;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.simplereport.SimpleReport;
import eu.europa.esig.dss.spi.signature.AdvancedSignature;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import eu.europa.esig.dss.validation.reports.Reports;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.recruitment.eform.DeclarationSignatureValidator;
import org.springframework.stereotype.Component;

import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.util.ArrayList;
import java.util.List;

/**
 * Offline PAdES validation (EU DSS): structure, integrity and the signing certificate with its
 * serialNumber attribute (the candidate-PESEL match input). No trust-list provisioning in v1 —
 * a FAILED indication (e.g. HASH_FAILURE of a modified document) rejects the upload, a passing
 * or INDETERMINATE indication (no trust anchors / no revocation online) passes with the
 * qualified-status determination explicitly deferred (recorded in the report as an M4b/M6 open
 * item). The validator output keeps check and indication names only — never certificate dumps
 * or report payloads (GDPR discipline, art. 9 RODO).
 */
@Component
@Slf4j
class DssSignatureValidator implements DeclarationSignatureValidator {
    private static final String CHECK_STRUCTURE = "PADES_STRUCTURE";
    private static final String CHECK_INTEGRITY = "SIGNATURE_INTEGRITY";
    private static final String CHECK_SIGNING_CERT = "SIGNING_CERTIFICATE";
    private static final String CHECK_SERIAL = "CERTIFICATE_SERIAL_NUMBER";

    @Override
    public ValidatedSignature validate(byte[] pdfBytes) {
        List<String> performed = new ArrayList<>(
            List.of(CHECK_STRUCTURE, CHECK_INTEGRITY, CHECK_SIGNING_CERT));
        List<String> failed = new ArrayList<>();

        try {
            SignedDocumentValidator validator = SignedDocumentValidator
                .fromDocument(new InMemoryDocument(pdfBytes, "declaration.pdf"));
            CommonCertificateVerifier certificateVerifier = new CommonCertificateVerifier();
            certificateVerifier.setAlertOnMissingRevocationData(
                new eu.europa.esig.dss.alert.LogOnStatusAlert());
            validator.setCertificateVerifier(certificateVerifier);
            Reports reports = validator.validateDocument();
            SimpleReport simpleReport = reports.getSimpleReport();

            List<String> signatureIds = simpleReport.getSignatureIdList();

            if (signatureIds.isEmpty()) {
                failed.add("SIGNATURE_MISSING");
                return new ValidatedSignature(false, null, null, performed, List.copyOf(failed));
            }

            String signerName = null;
            String serialNumber = null;
            boolean anyFailed = false;

            // cryptographic integrity of every found signature, straight from the diagnostic data:
            // an offline validation without trust anchors reports a modified document as
            // INDETERMINATE (unresolved chain), not FAILED — BasicSignature.isSignatureValid is the
            // evidence that the signed data actually matches the the PAdES byte ranges
            for (eu.europa.esig.dss.diagnostic.jaxb.XmlSignature diagnosticSignature
                : validator.getDiagnosticData().getSignatures()) {
                eu.europa.esig.dss.diagnostic.jaxb.XmlBasicSignature basicSignature =
                    diagnosticSignature.getBasicSignature();

                if (!Boolean.TRUE.equals(basicSignature.isSignatureIntact())
                    || !Boolean.TRUE.equals(basicSignature.isSignatureValid())) {
                    anyFailed = true;
                    failed.add("SIGNATURE_FAILED_INTEGRITY");
                }
            }

            for (String signatureId : signatureIds) {
                Indication indication = simpleReport.getIndication(signatureId);
                SubIndication subIndication = simpleReport.getSubIndication(signatureId);

                if (indication == Indication.FAILED) {
                    anyFailed = true;
                    failed.add(subIndication == null
                        ? "SIGNATURE_FAILED" : "SIGNATURE_FAILED_" + subIndication.name());
                } else if (indication != Indication.TOTAL_PASSED && indication != Indication.TOTAL_FAILED) {
                    // INDETERMINATE stays acceptable in the offline v1 verdict (no trust/revocation
                    // sources); the qualified determination is deferred and reviewed separately
                    failed.add(subIndication == null ? indication.name() : subIndication.name());
                }

                if (signerName == null && simpleReport.getSignedBy(signatureId) != null) {
                    signerName = simpleReport.getSignedBy(signatureId);
                }
            }

            AdvancedSignature signature = validator.getSignatures().isEmpty()
                ? null : validator.getSignatures().get(0);

            if (signature != null && signature.getSigningCertificateToken() != null) {
                CertificateToken signingCertificate = signature.getSigningCertificateToken();
                serialNumber = serialNumberOf(signingCertificate);
                performed.add(CHECK_SERIAL);

                if (serialNumber == null) {
                    failed.add("CERTIFICATE_SERIAL_NUMBER_MISSING");
                }
            }

            boolean valid = !anyFailed;

            return new ValidatedSignature(valid, signerName, serialNumber, List.copyOf(performed),
                List.copyOf(failed));
        } catch (RuntimeException analysisFailure) {
            // a non-PDF or a corrupt PAdES container is a candidate's upload problem, not ours
            log.warn("declaration signature analysis failed: {} ({})",
                analysisFailure.getClass().getSimpleName(), analysisFailure.getMessage());
            failed.add(CHECK_STRUCTURE);
            return new ValidatedSignature(false, null, null, performed, List.copyOf(failed));
        }
    }

    /**
     * The serialNumber RDN (2.5.4.5) of the subject: the personal identifier the qualified
     * certificate of a Polish citizen reveals (candidate-PESEL match policy of the owner). The
     * JDK X500Principal prints the RDN either in keyword form ({@code SERIALNUMBER=...}) or in
     * distinguished form ({@code 2.5.4.5=#...} with an ASN.1 PrintableString payload) — both are
     * handled, digits are returned without any transformation.
     */
    private static @Nullable String serialNumberOf(CertificateToken signingCertificate) {
        try {
            String rfc2253 = signingCertificate.getSubject().getRFC2253();
            LdapName subject = new LdapName(rfc2253);

            for (Rdn rdn : subject.getRdns()) {
                if ("serialnumber".equalsIgnoreCase(rdn.getType())
                    && !isDistinguishedValue(rdn)) {
                    return String.valueOf(rdn.getValue());
                }

                if ("2.5.4.5".equalsIgnoreCase(rdn.getType())) {
                    Object value = rdn.getValue();

                    if (value instanceof byte[] encoded) {
                        return decodeAsn1String(encoded);
                    }

                    return String.valueOf(value);
                }
            }

            return null;
        } catch (@SuppressWarnings("unused") javax.naming.InvalidNameException malformed) {
            return null;
        }
    }

    /** The quoted form ({@code SERIALNUMBER=#130b...}) stays distinguished: read the OID branch. */
    private static boolean isDistinguishedValue(Rdn rdn) {
        return rdn.getValue() instanceof byte[];
    }

    /** Simple ASN.1 string (PrintableString/UTF8String/IA5String, short length form). */
    private static @Nullable String decodeAsn1String(byte[] encoded) {
        if (encoded.length < 2) {
            return null;
        }

        int tag = encoded[0] & 0xFF;
        boolean stringTag = tag == 0x13 || tag == 0x0C || tag == 0x16;

        if (!stringTag) {
            return null;
        }

        int length = encoded[1] & 0xFF;

        if (length > encoded.length - 2) {
            return null;
        }

        return new String(encoded, 2, length, java.nio.charset.StandardCharsets.US_ASCII);
    }
}
