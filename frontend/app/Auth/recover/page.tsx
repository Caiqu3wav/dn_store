"use client";

import Link from "next/link";

export default function RecoverPasswordPage() {
  return (
    <div style={{ minHeight: "100vh", display: "flex", justifyContent: "center", alignItems: "center", background: "#fff" }}>
      <div style={{ width: "100%", maxWidth: 520, padding: 20 }}>
        <h1 style={{ fontSize: 28, fontWeight: 700, marginBottom: 12 }}>Esqueci minha senha</h1>
        <p style={{ color: "#444", marginBottom: 20 }}>
          Informe seu e-mail para receber um link de redefinição.
        </p>

        <div style={{ background: "#f7f7f7", padding: 20, borderRadius: 10, boxShadow: "0 6px 20px rgba(0, 0, 0, 0.2)" }}>
          <label style={{ display: "block", fontWeight: 700, marginBottom: 8 }}>Email</label>
          <input
            type="email"
            placeholder="user@example.com"
            style={{ width: "100%", padding: 12, borderRadius: 6, border: "1px solid #ccc", outline: "none" }}
          />

          <button
            type="button"
            style={{ marginTop: 16, width: "100%", padding: 12, border: "none", borderRadius: 6, background: "#ff0000", color: "#fff", cursor: "pointer" }}
          >
            Enviar link
          </button>

          <p style={{ marginTop: 14, textAlign: "center" }}>
            <Link href="/auth" style={{ color: "#007bff", textDecoration: "none" }}>
              Voltar
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}

