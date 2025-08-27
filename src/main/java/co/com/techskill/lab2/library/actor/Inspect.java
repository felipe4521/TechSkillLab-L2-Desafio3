package co.com.techskill.lab2.library.actor;

import co.com.techskill.lab2.library.domain.entity.Petition;
import reactor.core.publisher.Mono;

/*
* Interfaz para el actor INSPECT, igual a Actor.java
*/
public interface Inspect {
    boolean supports(String type); // "INSPECT"
    Mono<String> handle(Petition petition);
}
