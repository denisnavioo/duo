// Aplica el modo claro/oscuro antes de pintar la pagina, para que no parpadee.
// Si no se ha elegido ninguno, sigue al del sistema (como en iOS).
try {
  const m = localStorage.getItem("duo-modo");
  document.documentElement.dataset.modo = (m === "claro" || m === "oscuro") ? m
    : (matchMedia("(prefers-color-scheme: dark)").matches ? "oscuro" : "claro");
} catch (e) {}
