/* Duo · service worker: solo muestra los avisos que manda el servidor (no guarda paginas ni datos) */
self.addEventListener("install", () => self.skipWaiting());
self.addEventListener("activate", e => e.waitUntil(self.clients.claim()));

self.addEventListener("push", e => {
  let d = { titulo: "Duo", texto: "", url: "/calendario" };
  try { if (e.data) d = { ...d, ...e.data.json() }; } catch (err) { /* aviso sin formato: se muestra el generico */ }
  e.waitUntil(self.registration.showNotification(d.titulo, {
    body: d.texto,
    icon: "/icono-192.png",
    badge: "/icono-insignia.png",
    tag: "duo-manana",
    data: { url: d.url }
  }));
});

self.addEventListener("notificationclick", e => {
  e.notification.close();
  const url = new URL(e.notification.data?.url || "/calendario", self.location.origin).href;
  e.waitUntil((async () => {
    const abiertas = await self.clients.matchAll({ type: "window", includeUncontrolled: true });
    for (const c of abiertas) {
      if (new URL(c.url).origin === self.location.origin && "navigate" in c) {
        await c.focus();
        return c.navigate(url);
      }
    }
    return self.clients.openWindow(url);
  })());
});
