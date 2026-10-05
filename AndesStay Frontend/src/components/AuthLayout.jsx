import Brand from "./Brand";
import Icon from "./Icon";

export default function AuthLayout({ children, registration = false }) {
    return <main className={`login-page ${registration ? "register-page" : ""}`}>
        <section className="login-story" aria-label="Bienvenido a AndesStay">
            <Brand light />
            <div className="login-story-copy"><span className="story-tag"><Icon name="leaf" size={15} /> NATURALEZA. DESCANSO. CONEXIÓN.</span>
                <h1>{registration ? <>Tu próxima<br />estadía empieza<br /><em>contigo.</em></> : <>Un respiro.<br />Una nueva <em>estadía.</em></>}</h1>
                <p>{registration ? "Crea tu espacio en AndesStay y empieza a planificar nuevos recuerdos." : "El encanto de los Andes, la tranquilidad de sentirte en casa."}</p>
            </div>
            <div className="login-landscape" role="img" aria-label="Ilustración de una cabaña entre montañas y bosque" />
            <div className="story-footer"><span>DESCONÉCTATE PARA CONECTAR</span><span>AndesStay <span aria-hidden="true">↗</span></span></div>
        </section>
        <section className="login-panel">
            <div className="login-mobile-brand"><Brand /></div>
            <div className="login-form-content">{children}</div>
            <footer className="login-footer"><span>© {new Date().getFullYear()} AndesStay</span><span>Bienvenido a tu próximo destino.</span></footer>
        </section>
    </main>;
}
