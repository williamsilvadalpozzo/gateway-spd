package com.api.gateway.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TextoRespostaTest {

    @Test
    void jsonViraTextoEZipFicaSemTexto() {
        byte[] json = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        assertEquals("{\"ok\":true}",
                TextoResposta.comoTexto(json, "application/json; charset=UTF-8", "UTF-8"));

        byte[] zip = new byte[] {0x50, 0x4b, 0x03, 0x04};
        assertNull(TextoResposta.comoTexto(zip, "application/zip", null));
        assertNull(TextoResposta.comoTexto(zip, "application/octet-stream", null));
    }

    @Test
    void xmlUsaCharsetInformado() {
        byte[] xml = "<nfe>á</nfe>".getBytes(StandardCharsets.ISO_8859_1);
        assertEquals("<nfe>á</nfe>",
                TextoResposta.comoTexto(xml, "application/xml", "ISO-8859-1"));
    }
}
