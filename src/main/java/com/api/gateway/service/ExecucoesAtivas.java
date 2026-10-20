package com.api.gateway.service;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ExecucoesAtivas {

    private final Set<Long> ids = Collections.synchronizedSet(new HashSet<Long>());

    public void iniciar(long id) {
        ids.add(Long.valueOf(id));
    }

    public void encerrar(long id) {
        ids.remove(Long.valueOf(id));
    }

    public Set<Long> atuais() {
        synchronized (ids) {
            return new HashSet<Long>(ids);
        }
    }
}
