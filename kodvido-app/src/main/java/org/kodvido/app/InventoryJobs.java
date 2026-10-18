// SPDX-License-Identifier: GPL-3.0-only
package org.kodvido.app;
import org.kodvido.engine.InventoryService;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
@Component
public final class InventoryJobs {
    private final InventoryService service;
    private final boolean enabled;
    private final ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"kodvido-inventory");t.setDaemon(true);return t;});
    private final AtomicBoolean busy=new AtomicBoolean();
    private volatile String state="En attente";
    public InventoryJobs(InventoryService service,@Value("${kodvido.inventory.automatic:true}") boolean enabled) {this.service=service;this.enabled=enabled;}
    @PostConstruct void start() {if(enabled) executor.scheduleWithFixedDelay(()->run(service::refreshAll),2,1800,TimeUnit.SECONDS);}
    public void imported() {if(enabled) submit(null,false);}
    public String state() {return state;}
    public boolean submit(UUID id,boolean dependencies) {
        if(!busy.compareAndSet(false,true)) return false;
        state="Actualisation en cours";
        executor.submit(()->execute(()->{if(id==null) service.refreshAll();else service.refresh(id,dependencies);}));return true;
    }
    private void run(Runnable task) {if(busy.compareAndSet(false,true)) execute(task);}
    private void execute(Runnable task) {
        state="Actualisation en cours";
        try {task.run();state="Dernière actualisation terminée : "+java.time.Instant.now();}
        catch(Exception e) {state="Échec de l’actualisation ; données précédentes conservées.";}
        finally {busy.set(false);}
    }
    @PreDestroy void stop() {executor.shutdownNow();}
}
