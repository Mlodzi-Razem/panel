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

package org.mlodzirazem.panel.services.panelapi.recruitment.eform;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.Pkcs12SignatureToken;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Test fixture signer: a self-signed certificate with the serialNumber RDN (the mObywatel-style
 * subject attribute the validator's PESEL-match policy reads) and a PAdES-B-Baseline signature,
 * exactly the shape the DSS validator accepts. Synthetic credentials only.
 */
public final class PadesSignatures {
    private PadesSignatures() {
    }

    public static byte[] selfSignedP12(String commonName, String personSerialNumber, char[] password)
        throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();

        X500Name subject = new X500NameBuilder(BCStyle.INSTANCE)
            .addRDN(BCStyle.CN, commonName)
            .addRDN(BCStyle.SERIALNUMBER, personSerialNumber)
            .build();

        Instant now = Instant.now();
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(subject,
            BigInteger.valueOf(Math.abs(now.toEpochMilli())), Date.from(now.minus(Duration.ofDays(1))),
            Date.from(now.plus(Duration.ofDays(3650))), subject, pair.getPublic());

        X509Certificate certificate = new JcaX509CertificateConverter().getCertificate(
            builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate())));

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, password);
        keyStore.setKeyEntry("declaration", pair.getPrivate(), password, new Certificate[] {certificate});

        ByteArrayOutputStream p12 = new ByteArrayOutputStream();
        keyStore.store(p12, password);

        return p12.toByteArray();
    }

    public static byte[] sign(byte[] pdf, byte[] p12, char[] password) throws Exception {
        try (Pkcs12SignatureToken token = new Pkcs12SignatureToken(p12,
            new KeyStore.PasswordProtection(password))) {

            DSSPrivateKeyEntry keyEntry = token.getKeys().getFirst();
            CertificateToken certificate = keyEntry.getCertificate();

            PAdESService service = new PAdESService(new CommonCertificateVerifier());
            PAdESSignatureParameters parameters = new PAdESSignatureParameters();
            parameters.setSignatureLevel(SignatureLevel.PAdES_BASELINE_B);
            parameters.setDigestAlgorithm(DigestAlgorithm.SHA256);
            parameters.setSigningCertificate(certificate);
            parameters.setCertificateChain(keyEntry.getCertificateChain());

            DSSDocument toSign = new InMemoryDocument(pdf, "declaration.pdf");
            ToBeSigned dataToSign = service.getDataToSign(toSign, parameters);
            SignatureValue signatureValue = token.sign(dataToSign, parameters.getDigestAlgorithm(),
                keyEntry);

            DSSDocument signed = service.signDocument(toSign, parameters, signatureValue);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            signed.writeTo(out);

            return out.toByteArray();
        }
    }
}
