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

package org.mlodzirazem.panel.services.panelapi.bankimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.bankimport.BankStatementParseException.Code;

class BankStatementParserTest {
    private static final String OWN = "PL61109010140000071219812874";
    private static final String A = "PL86102010550000820123456789";
    private static final String B = "PL45124060690000100011112222";
    private static final String C = "PL27114020040000300201355387";
    private static final String D = "PL15160010120000000011112222";
    private static final String DE = "DE89370400440532013000";

    private final BankStatementParser parser = new BankStatementParser();

    private static byte[] resource(String name) {
        try (InputStream in = BankStatementParserTest.class.getResourceAsStream("/bankimport/" + name)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private StatementFile parse(byte[] data, String name) {
        return parser.parse(new ByteArrayInputStream(data), name);
    }

    private StatementFile parse(String name) {
        return parse(resource(name), name);
    }

    private StatementFile parse(String content, String name) {
        return parse(content.getBytes(StandardCharsets.UTF_8), name);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Test
    void parsesCamt053Version2() {
        StatementFile file = parse("camt053-v02.xml");

        assertThat(file.format()).isEqualTo(BankStatementFormat.CAMT_053);
        assertThat(file.accountIban()).isEqualTo(OWN);
        assertThat(file.currency()).isEqualTo("PLN");
        assertThat(file.statementId()).isEqualTo("STMT-2025-03");
        assertThat(file.openingBalance()).isEqualByComparingTo("1000.00");
        assertThat(file.closingBalance()).isEqualByComparingTo("1444.50");
        assertThat(file.from()).isEqualTo(LocalDate.of(2025, 3, 1));
        assertThat(file.to()).isEqualTo(LocalDate.of(2025, 3, 31));
        assertThat(file.transactions()).hasSize(7);

        BankTransaction credit = file.transactions().get(0);
        assertThat(credit.bookingDate()).isEqualTo(LocalDate.of(2025, 3, 3));
        assertThat(credit.valueDate()).isEqualTo(LocalDate.of(2025, 3, 3));
        assertThat(credit.amount()).isEqualByComparingTo("150.00");
        assertThat(credit.currency()).isEqualTo("PLN");
        assertThat(credit.counterpartyName()).isEqualTo("Jan Testowy");
        assertThat(credit.counterpartyIban()).isEqualTo(A);
        assertThat(credit.title()).isEqualTo("Skladka MR-12-3 za marzec");
        assertThat(credit.bankReference()).isEqualTo("REF-0001");
        assertThat(credit.stableKey()).isEqualTo("REF-0001");

        BankTransaction debit = file.transactions().get(1);
        assertThat(debit.amount()).isEqualByComparingTo("-45.50");
        assertThat(debit.valueDate()).isEqualTo(LocalDate.of(2025, 3, 6));
        assertThat(debit.counterpartyName()).isEqualTo("Fake Hosting Sp. z o.o.");
        assertThat(debit.counterpartyIban()).isEqualTo(DE);
        assertThat(debit.title()).isEqualTo("RF18539007547034");
    }

    @Test
    void splitsBatchEntriesIntoOneTransactionPerTxDtls() {
        List<BankTransaction> batch = parse("camt053-v02.xml").transactions().subList(2, 5);

        assertThat(batch).extracting(BankTransaction::amount)
            .usingElementComparator(BigDecimal::compareTo)
            .containsExactly(bd("100.00"), bd("120.00"), bd("80.00"));
        assertThat(batch).extracting(BankTransaction::counterpartyName)
            .containsExactly("Anna Przykladowa", "Piotr Fikcyjny", "Ewa Wymyslona");
        assertThat(batch).extracting(BankTransaction::counterpartyIban).containsExactly(B, C, null);
        assertThat(batch).extracting(BankTransaction::bankReference).containsOnly("REF-0003");
        assertThat(batch).extracting(BankTransaction::stableKey)
            .containsExactly("REF-0003#tx1", "REF-0003#tx2", "REF-0003#tx3");
    }

    @Test
    void parsesCamt053Version8() {
        StatementFile file = parse("camt053-v08.xml");

        assertThat(file.accountIban()).isEqualTo(OWN);
        assertThat(file.openingBalance()).isEqualByComparingTo("-500.00");
        assertThat(file.closingBalance()).isEqualByComparingTo("-796.00");
        assertThat(file.from()).isEqualTo(LocalDate.of(2025, 4, 1));
        assertThat(file.transactions()).hasSize(4);

        BankTransaction credit = file.transactions().get(0);
        assertThat(credit.bookingDate()).isEqualTo(LocalDate.of(2025, 4, 2));
        assertThat(credit.amount()).isEqualByComparingTo("250.00");
        assertThat(credit.counterpartyName()).isEqualTo("Maria Nieistniejąca");
        assertThat(credit.counterpartyIban()).isEqualTo(A);
        assertThat(credit.title()).isEqualTo("Składka członkowska MR-44-2");
        assertThat(credit.bankReference()).isEqualTo("V8-0001");

        BankTransaction first = file.transactions().get(1);
        BankTransaction second = file.transactions().get(2);
        assertThat(first.amount()).isEqualByComparingTo("-500.00");
        assertThat(first.counterpartyName()).isEqualTo("Fikcyjna Drukarnia");
        assertThat(first.counterpartyIban()).isEqualTo(D);
        assertThat(first.stableKey()).isEqualTo("V8-0002-A");
        assertThat(second.amount()).isEqualByComparingTo("-300.00");
        assertThat(second.counterpartyName()).isEqualTo("Wymyślone Biuro");
        assertThat(second.stableKey()).isEqualTo("V8-0002#tx2");

        BankTransaction unreferenced = file.transactions().get(3);
        assertThat(unreferenced.bankReference()).isNull();
        assertThat(unreferenced.valueDate()).isNull();
        assertThat(unreferenced.stableKey()).matches("[0-9a-f]{64}");
    }

    @Test
    void usesEndToEndIdWhenNoServicerReference() {
        String xml = new String(resource("camt053-v08.xml"), StandardCharsets.UTF_8)
            .replace("<AcctSvcrRef>V8-0001</AcctSvcrRef>", "");

        StatementFile file = parse(xml, "x.xml");

        assertThat(file.transactions().getFirst().bankReference()).isEqualTo("E2E-777");
        assertThat(file.transactions().getFirst().stableKey()).isEqualTo("E2E-777");
    }

    @Test
    void parsesMt940Utf8() {
        StatementFile file = parse("mt940-utf8.sta");
        assertMt940(file);
    }

    @Test
    void parsesMt940Windows1250() {
        byte[] bytes = resource("mt940-windows1250.sta");
        assertThat(new String(bytes, StandardCharsets.UTF_8)).contains("�");

        assertThat(parse(bytes, "mt940-windows1250.sta")).isEqualTo(parse("mt940-utf8.sta"));
    }

    @Test
    void parsesMt940Iso88592() {
        String text = new String(resource("mt940-utf8.sta"), StandardCharsets.UTF_8);
        byte[] bytes = text.getBytes(Charset.forName("ISO-8859-2"));

        assertThat(parse(bytes, "statement.sta")).isEqualTo(parse("mt940-utf8.sta"));
    }

    private void assertMt940(StatementFile file) {
        assertThat(file.format()).isEqualTo(BankStatementFormat.MT940);
        assertThat(file.accountIban()).isEqualTo(OWN);
        assertThat(file.currency()).isEqualTo("PLN");
        assertThat(file.statementId()).isEqualTo("STARTUMS/00012/001");
        assertThat(file.openingBalance()).isEqualByComparingTo("1000.00");
        assertThat(file.closingBalance()).isEqualByComparingTo("1095.50");
        assertThat(file.from()).isEqualTo(LocalDate.of(2025, 3, 1));
        assertThat(file.to()).isEqualTo(LocalDate.of(2025, 3, 31));
        assertThat(file.transactions()).hasSize(7);

        List<BankTransaction> tx = file.transactions();
        assertThat(tx.get(0).bookingDate()).isEqualTo(LocalDate.of(2025, 3, 3));
        assertThat(tx.get(0).amount()).isEqualByComparingTo("150.00");
        assertThat(tx.get(0).counterpartyName()).isEqualTo("Zażółć Gęślą Jaźń Testowa");
        assertThat(tx.get(0).counterpartyIban()).isEqualTo(A);
        assertThat(tx.get(0).title()).isEqualTo("Składka MR-12-3 za marzec 2025");
        assertThat(tx.get(0).bankReference()).isEqualTo("BANKREF0001");
        assertThat(tx.get(0).stableKey()).isEqualTo("BANKREF0001");

        assertThat(tx.get(1).amount()).isEqualByComparingTo("-45.50");
        assertThat(tx.get(1).counterpartyName()).isEqualTo("Fikcyjna Firma Sp. z o.o.");
        assertThat(tx.get(1).counterpartyIban()).isEqualTo(B);
        assertThat(tx.get(1).title()).isEqualTo("Faktura za hosting");

        assertThat(tx.get(2).bookingDate()).isEqualTo(LocalDate.of(2025, 3, 6));
        assertThat(tx.get(2).valueDate()).isEqualTo(LocalDate.of(2025, 3, 6));
        assertThat(tx.get(2).counterpartyName()).isNull();
        assertThat(tx.get(2).title()).isEqualTo("Wpłata gotówkowa na rachunek bez struktury");

        assertThat(tx.get(3).amount()).isEqualByComparingTo("-10.00");
        assertThat(tx.get(3).title()).isEqualTo("Zwrot błędnej wpłaty");
        assertThat(tx.get(3).counterpartyName()).isEqualTo("Żaneta Źródłowa");
        assertThat(tx.get(3).bankReference()).isEqualTo("BANKREF0004");

        assertThat(tx.get(4).amount()).isEqualByComparingTo("5.00");
        assertThat(tx.get(4).bankReference()).isNull();

        assertThat(tx.get(5).amount()).isEqualByComparingTo("-12.00");
        assertThat(tx.get(5).title()).isEqualTo("Prowizja za prowadzenie rachunku w marcu dalszy ciąg tytułu");
        assertThat(tx.get(5).title()).isEqualTo(tx.get(6).title());
    }

    @Test
    void parsesMt940WithSwiftEnvelopeAndAngleBracketSubfields() {
        String mt = "{1:F01TESTPLPWAXXX0000000000}{2:O9400000000000TESTPLPWAXXX00000000000000000000N}{4:\n"
            + ":20:REF1\n:25:61109010140000071219812874\n:60F:D250101PLN10,00\n"
            + ":61:2501020102C20,00NTRFNONREF//B1\n"
            + ":86:020<00Przelew<20Tytul pierwszy<2100123<32Firma Testowa<3810901014<3100000071219812874\n"
            + ":62F:C250102PLN10,00\n-}";

        StatementFile file = parse(mt, "x.txt");

        assertThat(file.accountIban()).isEqualTo(OWN);
        assertThat(file.openingBalance()).isEqualByComparingTo("-10.00");
        BankTransaction tx = file.transactions().getFirst();
        assertThat(tx.title()).isEqualTo("Tytul pierwszy 00123");
        assertThat(tx.counterpartyName()).isEqualTo("Firma Testowa");
        assertThat(tx.counterpartyIban()).isNull();
    }

    @Test
    void mt940CounterpartyFromBankIdAndAccount() {
        String mt = ":20:REF1\n:25:/PL61109010140000071219812874\n:60F:C250101PLN10,00\n"
            + ":61:250102C20,00NTRF//B1\n"
            + ":86:020~20Tytul~3010201055~3186102010550000820123456789~32Firma\n"
            + ":62F:C250102PLN30,00\n";

        assertThat(parse(mt, "x.sta").transactions().getFirst().counterpartyIban()).isEqualTo(A);
    }

    @Test
    void mt940EntryDateAcrossYearBoundary() {
        String mt = ":20:REF1\n:25:PL61109010140000071219812874\n:60F:C241230PLN10,00\n"
            + ":61:2501020102C20,00NTRF//B1\n:61:2412310102D1,00NTRF//B2\n:62F:C250102PLN29,00\n";

        List<BankTransaction> tx = parse(mt, "x.sta").transactions();

        assertThat(tx.get(0).bookingDate()).isEqualTo(LocalDate.of(2025, 1, 2));
        assertThat(tx.get(1).valueDate()).isEqualTo(LocalDate.of(2024, 12, 31));
        assertThat(tx.get(1).bookingDate()).isEqualTo(LocalDate.of(2025, 1, 2));
    }

    @Test
    void detectsFormatsRegardlessOfFileName() {
        assertThat(parse(resource("camt053-v02.xml"), "statement.txt").format()).isEqualTo(BankStatementFormat.CAMT_053);
        assertThat(parse(resource("mt940-utf8.sta"), "statement.xml").format()).isEqualTo(BankStatementFormat.MT940);
        assertThat(parse(resource("mt940-utf8.sta"), null).format()).isEqualTo(BankStatementFormat.MT940);
    }

    @Test
    void rejectsUnknownFormats() {
        assertCode(() -> parse("name;amount\nsomeone;10.00\n", "x.csv"), Code.UNKNOWN_FORMAT);
        assertCode(() -> parse("", "x.xml"), Code.UNKNOWN_FORMAT);
        assertCode(() -> parse("   \n", "x.sta"), Code.UNKNOWN_FORMAT);
        assertCode(() -> parse("<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.052.001.02\"><BkToCstmrAcctRpt/></Document>", "x.xml"),
            Code.UNKNOWN_FORMAT);
        assertCode(() -> parse("<html><body>hello</body></html>", "x.xml"), Code.UNKNOWN_FORMAT);
    }

    @Test
    void rejectsUnsupportedCamtVersion() {
        String xml = new String(resource("camt053-v02.xml"), StandardCharsets.UTF_8)
            .replace("camt.053.001.02", "camt.053.001.01");

        assertCode(() -> parse(xml, "x.xml"), Code.UNSUPPORTED_VERSION);
    }

    @Test
    void rejectsMalformedXml() {
        String xml = new String(resource("camt053-v02.xml"), StandardCharsets.UTF_8);

        assertCode(() -> parse(xml.substring(0, xml.length() / 2), "x.xml"), Code.MALFORMED);
    }

    @Test
    void rejectsExternalEntities() {
        String xml = "<?xml version=\"1.0\"?>\n<!DOCTYPE Document [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>\n"
            + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.053.001.02\"><BkToCstmrStmt><Stmt><Id>&xxe;</Id>"
            + "</Stmt></BkToCstmrStmt></Document>";

        assertThatThrownBy(() -> parse(xml, "x.xml"))
            .isInstanceOfSatisfying(BankStatementParseException.class, e -> {
                assertThat(e.getCode()).isEqualTo(Code.MALFORMED);
                assertThat(e.getMessage()).doesNotContain("root:");
            });
    }

    @Test
    void rejectsInternalEntityExpansion() {
        String xml = "<?xml version=\"1.0\"?>\n<!DOCTYPE Document [<!ENTITY a \"aaaa\"><!ENTITY b \"&a;&a;&a;&a;\">]>\n"
            + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.053.001.02\"><BkToCstmrStmt><Stmt><Id>&b;</Id>"
            + "</Stmt></BkToCstmrStmt></Document>";

        assertCode(() -> parse(xml, "x.xml"), Code.MALFORMED);
    }

    @Test
    void rejectsMixedCurrenciesInCamt() {
        String xml = new String(resource("camt053-v02.xml"), StandardCharsets.UTF_8)
            .replaceFirst("<Amt Ccy=\"PLN\">20.00</Amt>", "<Amt Ccy=\"EUR\">20.00</Amt>");

        assertCode(() -> parse(xml, "x.xml"), Code.MIXED_CURRENCIES);
    }

    @Test
    void rejectsMixedCurrenciesInMt940() {
        String mt = ":20:REF1\n:25:PL61109010140000071219812874\n:60F:C250101PLN10,00\n:62F:C250102EUR10,00\n";

        assertCode(() -> parse(mt, "x.sta"), Code.MIXED_CURRENCIES);
    }

    @Test
    void rejectsStatementsOfSeveralAccounts() {
        String mt = ":20:R1\n:25:PL61109010140000071219812874\n:60F:C250101PLN10,00\n:62F:C250102PLN10,00\n"
            + ":20:R2\n:25:PL86102010550000820123456789\n:60F:C250101PLN10,00\n:62F:C250102PLN10,00\n";

        assertCode(() -> parse(mt, "x.sta"), Code.MULTIPLE_ACCOUNTS);
    }

    @Test
    void mergesSeveralStatementsOfOneAccount() {
        String mt = ":20:R1\n:25:PL61109010140000071219812874\n:60F:C250101PLN10,00\n"
            + ":61:250102C5,00NTRF//B1\n:62F:C250102PLN15,00\n"
            + ":20:R2\n:25:PL61109010140000071219812874\n:60F:C250102PLN15,00\n"
            + ":61:250103C5,00NTRF//B2\n:62F:C250103PLN20,00\n";

        StatementFile file = parse(mt, "x.sta");

        assertThat(file.transactions()).hasSize(2);
        assertThat(file.openingBalance()).isEqualByComparingTo("10.00");
        assertThat(file.closingBalance()).isEqualByComparingTo("20.00");
        assertThat(file.from()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(file.to()).isEqualTo(LocalDate.of(2025, 1, 3));
    }

    @Test
    void errorMessagesDoNotLeakTransactionContents() {
        String mt = ":20:REF1\n:25:PL61109010140000071219812874\n:60F:C250101PLN10,00\n"
            + ":61:SECRETPERSON 123\n:86:SECRETPERSON\n:62F:C250102PLN10,00\n";

        assertThatThrownBy(() -> parse(mt, "x.sta"))
            .isInstanceOfSatisfying(BankStatementParseException.class, e -> {
                assertThat(e.getCode()).isEqualTo(Code.MALFORMED);
                assertThat(e.getMessage()).contains("line 4").doesNotContain("SECRETPERSON");
            });
    }

    @Test
    void rejectsInvalidAccount() {
        String mt = ":20:REF1\n:25:PL00109010140000071219812874\n:60F:C250101PLN10,00\n:62F:C250102PLN10,00\n";
        String xml = new String(resource("camt053-v02.xml"), StandardCharsets.UTF_8).replace(OWN, "PL00109010140000071219812874");

        assertCode(() -> parse(mt, "x.sta"), Code.MALFORMED);
        assertCode(() -> parse(xml, "x.xml"), Code.MALFORMED);
    }

    @Test
    void stableKeysAreStableAcrossRuns() {
        for (String name : List.of("camt053-v02.xml", "camt053-v08.xml", "mt940-utf8.sta")) {
            List<String> first = keys(parse(name));
            List<String> second = keys(parse(name));
            assertThat(second).isEqualTo(first);
            assertThat(first).doesNotHaveDuplicates();
        }
    }

    @Test
    void identicalTransactionsInOneFileGetDifferentKeys() {
        List<BankTransaction> camt = parse("camt053-v02.xml").transactions();
        assertThat(camt.get(5).title()).isEqualTo(camt.get(6).title());
        assertThat(camt.get(5).stableKey()).isNotEqualTo(camt.get(6).stableKey());

        List<BankTransaction> mt = parse("mt940-utf8.sta").transactions();
        assertThat(mt.get(5).stableKey()).isNotEqualTo(mt.get(6).stableKey());
        assertThat(mt.get(5).stableKey()).matches("[0-9a-f]{64}");
    }

    @Test
    void hashKeysIgnoreTitleWhitespaceAndCaseButNotAmount() {
        String base = ":20:R\n:25:PL61109010140000071219812874\n:60F:C250101PLN10,00\n:61:250102C5,00NTRF\n:86:Hello   World\n:62F:C250102PLN15,00\n";
        String variant = base.replace("Hello   World", "hello world");
        String other = base.replace("C5,00", "C6,00");

        String key = parse(base, "x.sta").transactions().getFirst().stableKey();

        assertThat(parse(variant, "x.sta").transactions().getFirst().stableKey()).isEqualTo(key);
        assertThat(parse(other, "x.sta").transactions().getFirst().stableKey()).isNotEqualTo(key);
    }

    @Test
    void duplicateBankReferencesWithinFileStayUnique() {
        String mt = ":20:R\n:25:PL61109010140000071219812874\n:60F:C250101PLN10,00\n"
            + ":61:250102C5,00NTRF//SAME\n:61:250102C6,00NTRF//SAME\n:62F:C250102PLN21,00\n";

        assertThat(keys(parse(mt, "x.sta"))).containsExactly("SAME", "SAME#2");
    }

    @Test
    void parsesLargeCamtFiles() {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\"?><Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.053.001.08\">"
            + "<BkToCstmrStmt><Stmt><Id>BIG</Id><Acct><Id><IBAN>" + OWN + "</IBAN></Id><Ccy>PLN</Ccy></Acct>");
        int count = 20000;
        for (int i = 0; i < count; i++) {
            xml.append("<Ntry><Amt Ccy=\"PLN\">1.00</Amt><CdtDbtInd>CRDT</CdtDbtInd><BookgDt><Dt>2025-01-02</Dt></BookgDt>")
                .append("<AcctSvcrRef>R").append(i).append("</AcctSvcrRef></Ntry>");
        }
        xml.append("</Stmt></BkToCstmrStmt></Document>");

        assertThat(parse(xml.toString(), "big.xml").transactions()).hasSize(count);
    }

    private static List<String> keys(StatementFile file) {
        return file.transactions().stream().map(BankTransaction::stableKey).collect(Collectors.toList());
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, Code code) {
        assertThatThrownBy(callable)
            .isInstanceOfSatisfying(BankStatementParseException.class, e -> assertThat(e.getCode()).isEqualTo(code));
    }
}
