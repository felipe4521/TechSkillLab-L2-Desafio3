package co.com.techskill.lab2.library.actor;

import co.com.techskill.lab2.library.domain.entity.Petition;
import co.com.techskill.lab2.library.repository.IBookRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Actor lógico para procesar peticiones de tipo "RETURN_INSPECT".
 * Es idéntico a ReturnActor pero implementa la interfaz Inspect.
 */
@Component
public class ReturnInspect implements Inspect {
    private final IBookRepository bookRepository;

    public ReturnInspect(IBookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @Override
    public boolean supports(String type) {
        return "RETURN_INSPECT".equals(type);
    }

    @Override
    public Mono<String> handle(Petition petition) {
        return Mono.zip(
                Mono.just(petition),
                bookRepository.findByBookId(petition.getBookId()))
                .delayElement(Duration.ofMillis(100))
                .map(t -> String.format("[RETURN_INSPECT] petition for book: %s with priority %d", t.getT2().getBookId(), t.getT1().getPriority()));
    }
}

