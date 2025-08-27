package co.com.techskill.lab2.library.service.impl;

import co.com.techskill.lab2.library.actor.Actor;
import co.com.techskill.lab2.library.repository.IPetitionRepository;
import co.com.techskill.lab2.library.service.IOrchestratorService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;

@Service
public class OrchestratorServiceImpl implements IOrchestratorService {
    private final IPetitionRepository petitionRepository;
    private final List<Actor> actors;

    public OrchestratorServiceImpl(IPetitionRepository petitionRepository, List<Actor> actors) {
        this.petitionRepository = petitionRepository;
        this.actors = actors;
    }

    @Override
    public Flux<String> orchestrate() {
        return petitionRepository.findAll()
                .limitRate(20)
                .publishOn(Schedulers.boundedElastic())
                .doOnSubscribe(s -> System.out.println("Inicio orquestación..."))
                .doOnNext(petition ->
                        System.out.println(String.format("Petición encontrada con ID: %s de tipo %s",
                                petition.getPetitionId(),petition.getType())))
                //Fan-out
                .groupBy(petition -> petition.getType()) //LEND / RETURN
                //Fan-in
                .flatMap(g -> {
                    String type = g.key();
                    Actor actor = actors.stream()
                            .filter(actor1 -> actor1.supports(type))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("No actor type " + type));
                    System.out.println("Agrupación por tipo: " + type);

                    if("LEND".equals(type) || "LEND_INSPECT".equals(type)){
                        return g.sort((a,b) -> Integer.compare(b.getPriority(), a.getPriority()))
                                .doOnNext(petition -> System.out.println(String.format("Petición [%s] con ID: %s en cola",
                                        type, petition.getPetitionId())))
                                .concatMap( petition -> actor.handle(petition)
                                        .doOnSubscribe(s -> System.out.println("Procesando petición con ID "+petition.getPetitionId())))
                                .doOnNext(res -> System.out.println("Proceso exitoso"))
                                .doOnError(err-> System.out.println("Procesamiento falló - "+err.getMessage()))
                                .onErrorContinue((err, p) -> System.out.println("Petición omitida " + err.getMessage()));
                    } else if ("RETURN".equals(type) || "RETURN_INSPECT".equals(type)) {
                        return g.sort((a,b) -> Integer.compare(b.getPriority(), a.getPriority()))
                                .doOnNext(petition -> System.out.println(String.format("Petición [%s] con ID: %s en cola",
                                        type, petition.getPetitionId())))
                                .concatMap( petition -> actor.handle(petition)
                                        .doOnSubscribe(s -> System.out.println("Procesando petición con ID "+petition.getPetitionId())))
                                .doOnNext(res -> System.out.println("Proceso exitoso"))
                                .doOnError(err-> System.out.println("Procesamiento falló - "+err.getMessage()))
                                .onErrorContinue((err, p) -> System.out.println("Petición omitida " + err.getMessage()));
                    } else if ("INSPECT".equals(type)) {
                        // Procesa INSPECT solo si priority >= 7, ordenando por prioridad descendente
                        return g.filter(petition -> petition.getPriority() != null && petition.getPriority() >= 7)
                                .sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
                                .doOnNext(petition -> System.out.println(String.format("Petición [INSPECT] con ID: %s en cola (priority %d)",
                                        petition.getPetitionId(), petition.getPriority())))
                                // flatMapSequential: paralelo pero preserva orden relativo
                                .flatMapSequential(petition -> actor.handle(petition)
                                        .doOnSubscribe(s -> System.out.println("Procesando petición de tipo [INSPECT] con ID "+petition.getPetitionId()))
                                        .doOnNext(res -> System.out.println("Proceso exitoso"))
                                        .doOnError(err-> System.out.println("Procesamiento falló - "+err.getMessage()))
                                        .onErrorResume(err -> Mono.just("Error en INSPECT: " + err.getMessage()))
                                )
                                .doOnError(err-> System.out.println("Procesamiento INSPECT falló - "+err.getMessage()));
                    } else {
                        return g.flatMap(petition -> actor.handle(petition)
                                .doOnSubscribe(s -> System.out.println("Procesando petición de tipo ["+type+"] con ID "+petition.getPetitionId()))
                                .doOnNext(res -> System.out.println("Proceso exitoso"))
                                .doOnError(err-> System.out.println("Procesamiento falló - "+err.getMessage())),
                                4)
                                .onErrorContinue((err, p) -> System.out.println("Petición omitida " + err.getMessage()));
                    }

                }).timeout(Duration.ofSeconds(5), Flux.just("Timeout exceeded")) //Control
                .doOnNext(s -> System.out.println("Next: "+s))
                .onErrorResume(err-> Flux.just("Error - "+ err.getMessage()))
                .doOnComplete(() -> System.out.println("Orchestration complete"));
    }
}
