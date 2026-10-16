import { beforeEach, afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import Register from "../pages/Register";
import Login from "../pages/Login";
const session=vi.hoisted(()=>({
    authenticated:false,
    account:{name:"Ana Torres",username:"verified@example.test",idTokenClaims:{roles:["Huesped"]}},
    instance:{loginRedirect:vi.fn(),acquireTokenSilent:vi.fn()},
}));
vi.mock("@azure/msal-react",()=>({
    useMsal:()=>({instance:session.instance,accounts:session.authenticated?[session.account]:[]}),
    useIsAuthenticated:()=>session.authenticated,
}));
let calls, responder;
beforeEach(()=>{
    vi.clearAllMocks();
    session.authenticated=false;
    session.account={name:"Ana Torres",username:"verified@example.test",idTokenClaims:{roles:["Huesped"]}};
    session.instance.acquireTokenSilent.mockResolvedValue({accessToken:"verified-token"});
    calls=[];
    responder=(_url,options)=>new Response(JSON.stringify(options.method==="POST"
        ?{registered:true,displayName:"Ana Torres",email:"verified@example.test",roles:["Huesped"]}
        :{registered:false,displayName:"Ana Torres",email:"verified@example.test",roles:[]}),{status:options.method==="POST"?201:200});
    vi.stubGlobal("fetch",vi.fn(async(path,options)=>{
        const url=new URL(path,"http://localhost");calls.push({url,options});return responder(url,options);
    }));
});
afterEach(()=>vi.unstubAllGlobals());
const show=component=>render(<MemoryRouter initialEntries={["/register"]}>{component}</MemoryRouter>);
describe("Registro con identidad Microsoft",()=>{
    it("ofrece el acceso desde login y espera a Microsoft antes de usar la API",()=>{
        const login=show(<Login />);
        expect(screen.getByRole("link",{name:"Crear mi perfil en AndesStay"}).getAttribute("href")).toBe("/register");
        login.unmount();show(<Register />);
        fireEvent.click(screen.getByRole("button",{name:"Registrarme con mi correo"}));
        expect(session.instance.loginRedirect).toHaveBeenCalledWith(expect.objectContaining({
            prompt:"create",redirectStartPage:new URL("/register",window.location.origin).href,
        }));
        expect(calls).toHaveLength(0);
    });
    it("permite reintentar una conexión fallida",async()=>{
        session.instance.loginRedirect.mockRejectedValue(new Error("offline"));
        show(<Register />);fireEvent.click(screen.getByRole("button",{name:"Registrarme con mi correo"}));
        expect((await screen.findByRole("alert")).textContent).toContain("Vuelve a intentarlo");
        expect(screen.getByRole("button",{name:"Registrarme con mi correo"}).disabled).toBe(false);
    });
    it("registra solo el nombre con bearer y conserva la cuenta verificada como solo lectura",async()=>{
        session.authenticated=true;show(<Register />);
        const name=await screen.findByLabelText("Nombre completo");
        const email=screen.getByLabelText("Correo verificado");
        expect(email.readOnly).toBe(true);expect(email.value).toBe("verified@example.test");
        fireEvent.change(name,{target:{value:"  Ana Torres  "}});
        fireEvent.click(screen.getByRole("button",{name:"Completar registro"}));
        await screen.findByText("Tu perfil se registró correctamente.");
        const post=calls.find(call=>call.options.method==="POST");
        expect(post.url.pathname).toBe("/api/v1/bff/account/register");
        expect(JSON.parse(post.options.body)).toEqual({displayName:"Ana Torres"});
        expect(post.options.headers.Authorization).toBe("Bearer verified-token");
        expect(screen.queryByLabelText(/contraseña/i)).toBeNull();
        expect(screen.getByRole("link",{name:"Ir a mi espacio"}).getAttribute("href")).toBe("/dashboard");
    });
    it("muestra el perfil existente y evita ofrecer un registro duplicado",async()=>{
        session.authenticated=true;
        responder=()=>new Response('{"registered":true,"displayName":"Ana Torres","email":"verified@example.test"}',{status:200});
        show(<Register />);await screen.findByText("Tu perfil está listo.");
        expect(screen.queryByRole("button",{name:"Completar registro"})).toBeNull();
        expect(calls.every(call=>call.options.method==="GET")).toBe(true);
    });
    it("un perfil nuevo sin roles de Entra obtiene acceso como huésped",async()=>{
        session.authenticated=true;session.account.idTokenClaims.roles=[];
        show(<Register />);await screen.findByLabelText("Nombre completo");
        fireEvent.click(screen.getByRole("button",{name:"Completar registro"}));
        await screen.findByText(/Tu acceso como huésped está listo/);
        expect(screen.getByRole("link",{name:"Ir a mi espacio"})).toBeTruthy();
    });
    it("un conflicto comprueba el perfil existente sin reenviar el registro",async()=>{
        session.authenticated=true;let registered=false;
        responder=(_url,options)=>{
            if(options.method==="POST"){registered=true;return new Response('{"detail":"Tu cuenta ya tiene un perfil en AndesStay."}',{status:409});}
            return new Response(JSON.stringify({registered,displayName:"Ana Torres",email:"verified@example.test"}),{status:200});
        };
        show(<Register />);await screen.findByLabelText("Nombre completo");
        fireEvent.click(screen.getByRole("button",{name:"Completar registro"}));
        await screen.findByText("Tu perfil está listo.");
        await waitFor(()=>expect(calls.filter(call=>call.options.method==="GET")).toHaveLength(2));
        expect(calls.filter(call=>call.options.method==="POST")).toHaveLength(1);
    });
});
