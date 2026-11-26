package com.pareja.duo.repartir;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calcula cuánto ha pagado cada uno, cuánto le toca y quién tiene que pasarle dinero a quién.
 * Se trabaja en céntimos para que las cuentas cuadren exactas: si un gasto no se divide justo
 * (8 € entre 3), los céntimos que sobran se los lleva el primero de la lista.
 * Las transferencias se calculan para que haya el menor número posible de Bizums.
 */
public final class CalculoDeudas {

    public record Gasto(BigDecimal importe, String pagador, Collection<String> participantes) {}

    public record Pago(String de, String para, BigDecimal importe) {}

    /** pagado: lo que puso; leToca: lo que consumió; saldo: positivo = le deben, negativo = debe (ya contando los Bizums hechos). */
    public record Saldo(String persona, BigDecimal pagado, BigDecimal leToca, BigDecimal saldo) {}

    public record Transferencia(String de, String para, BigDecimal importe) {}

    public record Resultado(List<Saldo> saldos, List<Transferencia> transferencias) {}

    private CalculoDeudas() {}

    public static Resultado calcular(List<String> personas, List<Gasto> gastos, List<Pago> pagos) {
        Map<String, Long> pagado = ceros(personas), toca = ceros(personas), ajuste = ceros(personas);

        for (Gasto g : gastos) {
            long total = centimos(g.importe());
            List<String> entre = personas.stream().filter(g.participantes()::contains).toList();
            if (entre.isEmpty()) {
                continue;
            }
            pagado.merge(g.pagador(), total, Long::sum);
            long parte = total / entre.size(), sobra = total % entre.size();
            for (int i = 0; i < entre.size(); i++) {
                toca.merge(entre.get(i), parte + (i < sobra ? 1 : 0), Long::sum);
            }
        }
        for (Pago p : pagos) {
            long c = centimos(p.importe());
            ajuste.merge(p.de(), c, Long::sum);
            ajuste.merge(p.para(), -c, Long::sum);
        }

        List<Saldo> saldos = new ArrayList<>();
        Map<String, Long> neto = new LinkedHashMap<>();
        for (String persona : personas) {
            long s = pagado.get(persona) - toca.get(persona) + ajuste.get(persona);
            neto.put(persona, s);
            saldos.add(new Saldo(persona, euros(pagado.get(persona)), euros(toca.get(persona)), euros(s)));
        }
        return new Resultado(saldos, transferencias(personas, neto));
    }

    /** Empareja al que más debe con al que más le deben, hasta que todo queda a cero. */
    private static List<Transferencia> transferencias(List<String> personas, Map<String, Long> neto) {
        Comparator<long[]> mayorPrimero = Comparator.<long[]>comparingLong(x -> -x[1]).thenComparingLong(x -> x[0]);
        List<long[]> acreedores = new ArrayList<>(), deudores = new ArrayList<>();
        for (int i = 0; i < personas.size(); i++) {
            long s = neto.get(personas.get(i));
            if (s > 0) acreedores.add(new long[] {i, s});
            if (s < 0) deudores.add(new long[] {i, -s});
        }
        acreedores.sort(mayorPrimero);
        deudores.sort(mayorPrimero);

        List<Transferencia> lista = new ArrayList<>();
        int a = 0, d = 0;
        while (a < acreedores.size() && d < deudores.size()) {
            long[] cobra = acreedores.get(a), paga = deudores.get(d);
            long c = Math.min(cobra[1], paga[1]);
            lista.add(new Transferencia(personas.get((int) paga[0]), personas.get((int) cobra[0]), euros(c)));
            cobra[1] -= c;
            paga[1] -= c;
            if (cobra[1] == 0) a++;
            if (paga[1] == 0) d++;
        }
        return lista;
    }

    private static Map<String, Long> ceros(List<String> personas) {
        Map<String, Long> m = new LinkedHashMap<>();
        personas.forEach(p -> m.put(p, 0L));
        return m;
    }

    private static long centimos(BigDecimal v) {
        return v.movePointRight(2).longValueExact();
    }

    private static BigDecimal euros(long centimos) {
        return BigDecimal.valueOf(centimos, 2);
    }
}
