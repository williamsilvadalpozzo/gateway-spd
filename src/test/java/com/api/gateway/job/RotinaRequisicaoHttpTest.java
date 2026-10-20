package com.api.gateway.job;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.api.gateway.http.CertificadosCliente;
import com.api.gateway.service.NomeInstancia;
import com.api.gateway.service.RequisicaoHttpService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RotinaRequisicaoHttpTest {

    @Test
    void consomeEnquantoHouverPendenteEMarcaInterrompidas() {
        RequisicaoHttpService service = Mockito.mock(RequisicaoHttpService.class);
        when(service.consumirUm()).thenReturn(Boolean.TRUE, Boolean.FALSE);
        when(service.marcarInterrompidas()).thenReturn(Integer.valueOf(2));
        RotinaRequisicaoHttp rotina = new RotinaRequisicaoHttp(
                service,
                Mockito.mock(CertificadosCliente.class),
                Mockito.mock(NomeInstancia.class));

        rotina.consumir();
        rotina.varrer();

        verify(service, times(2)).consumirUm();
        verify(service).marcarInterrompidas();
    }
}
