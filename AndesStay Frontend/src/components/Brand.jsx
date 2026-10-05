export default function Brand({ light = false }) {
    return <span className={`brand ${light ? "brand-light" : ""}`}>
        <svg viewBox="0 0 44 44" width="44" height="44" fill="none" aria-hidden="true">
            <rect width="44" height="44" rx="13" fill="currentColor" />
            <path d="m8 30 12-18 8 12 4-6 5 12H8Z" stroke="var(--brand-mark, #fff)" strokeWidth="1.7" strokeLinejoin="round" />
            <path d="m16 18 4 3 4-3" stroke="var(--brand-mark, #fff)" strokeWidth="1.5" />
        </svg><span>Andes<span className="brand-weight">Stay</span><small>ESTADÍAS & EXPERIENCIAS</small></span>
    </span>;
}
