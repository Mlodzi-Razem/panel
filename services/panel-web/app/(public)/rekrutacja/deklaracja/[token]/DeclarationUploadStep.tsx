"use client";

/*
 * Copyright (C) 2026 Stowarzyszenie Młodzi Razem
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

import React, {useRef, useState} from "react";
import {useRouter} from "next/navigation";
import {useLocale, useTranslations} from "next-intl";
import {ArrowSquareOutIcon, FilePdfIcon, UploadSimpleIcon} from "@phosphor-icons/react/ssr";
import Button from "@/components/button/Button";
import {Notice} from "@/components/page/Page";
import {DeclarationStatusKind} from "./DeclarationScreens";
import styles from "./DeclarationPage.module.scss";

/** Signed-PDF ceiling of this proxy (10 MiB); the mObywatel/eDO outputs are ~5 MiB. */
const MAX_UPLOAD_BYTES = 10 * 1024 * 1024;

/** „1,2 MB" / „345 kB" per locale — used in the file chip (prototype 4b). */
function formatFileSize(locale: string, bytes: number): string {
    const megabyte = 1024 * 1024;
    if (bytes >= megabyte) {
        return new Intl.NumberFormat(locale, {style: "unit", unit: "megabyte", maximumFractionDigits: 1}).format(bytes / megabyte);
    }
    return new Intl.NumberFormat(locale, {style: "unit", unit: "kilobyte", maximumFractionDigits: 0}).format(bytes / 1024);
}

/**
 * Step 3: render the filled declaration PDF (through the public proxy route), instruct the candidate
 * to sign it with a qualified signature (mObywatel / eDO) OUTSIDE the panel, and upload the signed
 * file. On server-side signature validation failure (SIGNATURE_INVALID) the candidate may retry;
 * on success the wizard lets the server take over: router.refresh() re-fetches the case and the
 * page swaps to the final "Deklaracja złożona" screen rendered from the SIGNED status.
 */
export default function DeclarationUploadStep({token, onFatal}: {
    token: string;
    onFatal: (kind: DeclarationStatusKind) => void;
}) {
    const t = useTranslations("recruitmentDeclaration.upload");
    const locale = useLocale();
    const [file, setFile] = useState<File | null>(null);
    const [dragging, setDragging] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [notice, setNotice] = useState<string | null>(null);
    const [uploading, setUploading] = useState(false);
    const [sentOk, setSentOk] = useState(false);
    const fileInput = useRef<HTMLInputElement>(null);
    const router = useRouter();

    const pdfUrl = `/api/recruitment/declaration-form/${encodeURIComponent(token)}/pdf`;
    const declarationUrl = `/api/recruitment/declaration-form/${encodeURIComponent(token)}/declaration`;

    function pickFile(candidate: File | null | undefined) {
        if (!candidate) {
            return;
        }
        setNotice(null);
        if (candidate.type !== "application/pdf" && !candidate.name.toLowerCase().endsWith(".pdf")) {
            setError(t("errFileType"));
            setFile(null);
            return;
        }
        if (candidate.size > MAX_UPLOAD_BYTES) {
            setError(t("errFileSize"));
            setFile(null);
            return;
        }
        setError(null);
        setFile(candidate);
    }

    function clearFile() {
        setFile(null);
        setError(null);
        setNotice(null);
        if (fileInput.current) {
            fileInput.current.value = "";
        }
    }

    async function send() {
        if (!file || uploading) {
            return;
        }
        setError(null);
        setNotice(null);
        setUploading(true);
        try {
            const formData = new FormData();
            formData.append("file", file);
            const response = await fetch(declarationUrl, {method: "POST", body: formData});
            if (response.status === 200) {
                setSentOk(true);
                router.refresh();
                return;
            }
            let code: string | null = null;
            try {
                code = String((await response.json()).code ?? "");
            } catch {
                code = "";
            }
            switch (code ?? "") {
                case "SIGNATURE_INVALID":
                    setNotice(t("errSignatureInvalid"));
                    return;
                case "INVALID_FILE_TYPE":
                    setError(t("errFileType"));
                    setFile(null);
                    return;
                case "FILE_TOO_LARGE":
                case "FILE_MISSING":
                    setError(t("errFileSize"));
                    setFile(null);
                    return;
                case "TOKEN_EXPIRED":
                    onFatal("expired");
                    return;
                case "CASE_CLOSED":
                    onFatal("closed");
                    return;
                case "LINK_UNAVAILABLE":
                    onFatal("unavailable");
                    return;
                default:
                    setNotice(t("errUpload"));
            }
        } catch {
            setNotice(t("errUpload"));
        } finally {
            setUploading(false);
        }
    }

    return <>
        <section className={styles.card}>
            <h1 className={styles.cardTitle}>{t("pdfTitle")}</h1>
            <p className={styles.cardHint}>{t("pdfHint")}</p>
            <iframe className={styles.pdfFrame} src={pdfUrl} title={t("pdfFrameTitle")}/>
            <a className={styles.linkButton} href={pdfUrl} target="_blank" rel="noopener">
                <ArrowSquareOutIcon aria-hidden/>
                {t("openPdf")}
            </a>
        </section>

        <section className={styles.card}>
            <h2 className={styles.cardTitle}>{t("signTitle")}</h2>
            <p className={styles.cardHint}>{t("signIntro")}</p>
            <ul className={styles.infoDutyList}>
                <li>{t("signStepApp")}</li>
                <li>{t("signStepWeb")}</li>
                <li>{t("signStepEdo")}</li>
            </ul>
            <p className={styles.cardHint}>{t("signNote")}</p>
        </section>

        <section className={styles.card}>
            <h2 className={styles.cardTitle}>{t("uploadTitle")}</h2>
            <div className={styles.dropzone + (dragging ? ` ${styles.dropzoneActive}` : "")}
                 onDragOver={event => {
                     event.preventDefault();
                     setDragging(true);
                 }}
                 onDragLeave={() => setDragging(false)}
                 onDrop={event => {
                     event.preventDefault();
                     setDragging(false);
                     pickFile(event.dataTransfer.files?.[0]);
                 }}>
                <UploadSimpleIcon aria-hidden size={28} color="var(--color-primary)"/>
                <p className={styles.cardHint}>{t("dropLabel")}</p>
                <label className={styles.dropzoneButton}>
                    <FilePdfIcon aria-hidden/>
                    {t("chooseFile")}
                    <input ref={fileInput}
                           type="file"
                           accept="application/pdf,.pdf"
                           className={styles.visuallyHidden}
                           onChange={event => pickFile(event.target.files?.[0])}/>
                </label>
            </div>

            {file && !sentOk && <p className={styles.fileChip}>
                <FilePdfIcon aria-hidden/>
                <span>{file.name}</span>
                <span className={styles.fileChipMeta}>{formatFileSize(locale, file.size)}</span>
                <Button variant="ghost" className={styles.fileChipRemove} onClick={clearFile}>
                    {t("removeFile")}
                </Button>
            </p>}

            {error && <Notice tone="danger" role="alert"><p>{error}</p></Notice>}
            {notice && <Notice tone="warning" role="alert"><p>{notice}</p></Notice>}
            {sentOk && <Notice tone="success" role="status"><p className={styles.successText}>{t("sentOk")}</p></Notice>}

            {!sentOk && <div className={styles.actions}>
                <Button iconLeft={<UploadSimpleIcon aria-hidden/>} disabled={!file || uploading} onClick={send}>
                    {t("sendLabel")}
                </Button>
                {uploading && <span className={styles.progressStatus}>{t("uploading")}</span>}
            </div>}
        </section>
    </>;
}
