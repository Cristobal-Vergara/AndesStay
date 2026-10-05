import Icon from "./Icon";
export default function MicrosoftButton({ children, busy, onClick }) {
    return <button type="button" className="microsoft-button" disabled={busy} onClick={onClick}>
        <svg width="19" height="19" viewBox="0 0 20 20" aria-hidden="true"><path fill="#f25022" d="M0 0h9v9H0z" /><path fill="#7fba00" d="M11 0h9v9h-9z" /><path fill="#00a4ef" d="M0 11h9v9H0z" /><path fill="#ffb900" d="M11 11h9v9h-9z" /></svg>
        {busy ? "Conectando con Microsoft…" : children}<Icon name="arrow" size={18} />
    </button>;
}
