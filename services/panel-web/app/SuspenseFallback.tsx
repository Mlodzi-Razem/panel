import Centered from "@/components/layout/centered/Centered";
import Spinner from "@/components/progress/spinner/Spinner";
import { useTranslations } from "next-intl";

export default function SuspenseFallback() {
    const t = useTranslations('suspense');
    return <div style={{
        width: '100%',
        height: '100%'
    }}>
        <Centered>
            <Spinner/>
            <p>{t('loading')}</p>
        </Centered>
    </div>;
}