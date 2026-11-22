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

import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCase;
import org.mlodzirazem.panel.services.panelapi.recruitment.eform.DeclarationPdfRenderer;
import org.mlodzirazem.panel.services.panelapi.recruitment.eform.RecruitmentEformRules;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.function.Supplier;

/**
 * Server-side rendering of the declaration PDF, paper-faithful layout of the versioned template
 * "MR-deklaracja-2026.1" (owner decisions of 2026-11-22): no correspondence address block, the
 * under-16 parent block dropped, the fill date canonic and server-set, no miejscowość field.
 * The supporting-membership variant is a template marker only (outside the workflow engine).
 * The RODO information duty lives in the footer of the rendered document.
 */
@Component
class OpenPdfDeclarationRenderer implements DeclarationPdfRenderer {
    private static final int FONT_BODY = 10;
    private static final int FONT_SMALL = 8;
    private static final int FONT_TITLE = 16;

    private final Supplier<LocalDate> today;

    OpenPdfDeclarationRenderer() {
        this(LocalDate::now);
    }

    OpenPdfDeclarationRenderer(Supplier<LocalDate> today) {
        this.today = today;
    }

    @Override
    public byte[] render(TRecruitmentCase candidateCase, @Nullable String templateVariant) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            BaseFont base = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1250,
                BaseFont.NOT_EMBEDDED);
            Font body = new Font(base, FONT_BODY);
            Font bold = new Font(base, FONT_BODY, Font.BOLD);
            Font small = new Font(base, FONT_SMALL);
            Font title = new Font(base, FONT_TITLE, Font.BOLD);

            Document document = new Document(PageSize.A4, 56, 56, 56, 64);
            PdfWriter.getInstance(document, out);
            document.open();

            addCentered(document, new Paragraph("DEKLARACJA CZŁONKOWSKA", title));
            document.add(emptyLine(body));

            Paragraph intro = new Paragraph("Wypełniając ten dokument, oświadczam chęć "
                + "przystąpienia w poczet osób członkowskich Stowarzyszenia Młodzi Razem.", body);
            document.add(intro);
            document.add(new Paragraph("Zobowiązuję się do przestrzegania Statutu Stowarzyszenia, "
                + "aktywnego uczestnictwa w realizacji jego celów, regularnego opłacania składki "
                + "oraz działania na rzecz budowania dobrego imienia Stowarzyszenia.", body));
            document.add(emptyLine(body));

            Paragraph dateLine = new Paragraph(
                RecruitmentEformRules.declarationDateLine(today.get()), body);
            dateLine.setAlignment(Element.ALIGN_RIGHT);
            document.add(dateLine);
            document.add(emptyLine(body));

            document.add(new Paragraph("A. DANE PERSONALNE", bold));
            document.add(new Paragraph("W polach A.1 i A.2 wpisać dane urzędowe („w dowodzie”).",
                small));
            addField(document, small, "A.1. Imię / imiona", candidateCase.getCandidateFormalFirstName());
            addField(document, small, "A.2. Nazwisko", candidateCase.getCandidateFormalLastName());
            addField(document, small, "A.3. Preferowane imię / imiona",
                candidateCase.getCandidatePreferredFirstName());
            addField(document, small, "A.4. Preferowane nazwisko",
                candidateCase.getCandidatePreferredLastName());
            addField(document, small, "A.5. Numer PESEL", candidateCase.getCandidateFormalIdentifier());
            addField(document, small, "A.6. Używane zaimki", candidateCase.getCandidatePronouns());
            document.add(emptyLine(body));

            document.add(new Paragraph("B. DANE KONTAKTOWE", bold));
            addField(document, small, "B.1. Numer telefonu", candidateCase.getCandidatePhone());
            addField(document, small, "B.2. Adres poczty elektronicznej (e-mail)",
                candidateCase.getCandidateEmail());
            document.add(emptyLine(body));

            document.add(new Paragraph("Do deklaracji dołączam:", body));
            document.add(new Paragraph("     zgodę na przetwarzanie danych osobowych", body));
            document.add(emptyLine(body));
            document.add(emptyLine(body));

            Paragraph signature = new Paragraph("Miejsce na podpis", body);
            signature.setAlignment(Element.ALIGN_RIGHT);
            document.add(signature);

            if (RecruitmentEformRules.TEMPLATE_VARIANT_SUPPORTING.equals(templateVariant)) {
                document.add(emptyLine(body));
                document.add(new Paragraph("Wariant: członkostwo wspierające "
                    + "(oznaczenie szablonu, poza silnikiem workflow)", small));
            }

            addRodoFooter(document, small, body);
            document.close();

            return out.toByteArray();
        } catch (DocumentException | java.io.IOException renderingFailure) {
            throw new IllegalStateException("Cannot render the declaration PDF", renderingFailure);
        }
    }

    /**
     * The information duty of the Zgoda document (admin, IOD, processors) in the footer — the
     * full duty text is shown on the form landing page; the PDF keeps the canonical clause.
     */
    private void addRodoFooter(Document document, Font small, Font body) throws DocumentException {
        document.add(emptyLine(body));
        Paragraph rule = new Paragraph(
            "Obowiązek informacyjny: administratorem danych osobowych jest Stowarzyszenie Młodzi "
                + "Razem, aleja Solidarności 68/121, 00-240 Warszawa (kontakt@mlodzirazem.org); "
                + "Inspektor Ochrony Danych: iodo@mlodzirazem.org. Podstawy przetwarzania: RODO "
                + "(rozporządzenie (UE) 2016/679) oraz ustawa z 7 kwietnia 1989 r. Prawo o "
                + "stowarzyszeniach; cele i okresy — zgodnie z obowiązkiem informacyjnym "
                + "Stowarzyszenia. Dane mogą być powierzane dostawcom usług informatycznych "
                + "(m.in. X Corp., Microsoft, Google, Meta Products Limited). Przysługują Ci "
                + "prawa: dostępu do danych, sprostowania, usunięcia, ograniczenia przetwarzania, "
                + "sprzeciwu, przenoszenia; skargę można wnieść do Prezesa UODO. Podanie danych "
                + "jest konieczne, aby zostać członkiem Stowarzyszenia; zgoda na wizerunek jest "
                + "dobrowolna.", small);
        document.add(rule);
        document.add(new Paragraph("Szablon: " + RecruitmentEformRules.TEMPLATE_VERSION, small));
    }

    private static void addField(
        Document document,
        Font small,
        String label,
        @Nullable String value
    ) throws DocumentException {
        String shown = value == null || value.isBlank() ? "—" : value;
        Paragraph field = new Paragraph();
        field.add(new Phrase(label + ": ", small));
        field.add(new Phrase(shown, small));
        document.add(field);
    }

    private static void addCentered(Document document, Paragraph paragraph) throws DocumentException {
        paragraph.setAlignment(Element.ALIGN_CENTER);
        document.add(paragraph);
    }

    private static Paragraph emptyLine(Font body) {
        Paragraph blank = new Paragraph(" ", body);
        blank.setLeading(14);
        return blank;
    }
}
