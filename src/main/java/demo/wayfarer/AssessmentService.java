package demo.wayfarer;

import ai.loomspan.api.SkillTemplate;
import org.springframework.stereotype.Service;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import jakarta.annotation.PreDestroy;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static demo.wayfarer.Contracts.*;

@Service
public class AssessmentService {
    private static final Logger log=LoggerFactory.getLogger(AssessmentService.class);
    private final TripStore store; private final SkillTemplate skills; private final ObjectMapper json;
    private final ThreadPoolExecutor executor=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8));
    private final Set<String> submitted=ConcurrentHashMap.newKeySet();
    public AssessmentService(TripStore store,SkillTemplate skills,ObjectMapper json) {this.store=store;this.skills=skills;this.json=json;}
    @EventListener(ApplicationReadyEvent.class) public void recover() {store.recoverInterrupted();}
    @PreDestroy public void close() {executor.shutdownNow();}
    public AssessmentView start(String tripId,int revision) {
        var assessment=store.begin(tripId,revision);
        if("QUEUED".equals(assessment.status()) && submitted.add(assessment.id())) {
            try {executor.execute(()->{try {run(assessment.id());} finally {submitted.remove(assessment.id());}});}
            catch(RejectedExecutionException e) {submitted.remove(assessment.id());store.fail(assessment.id(),"Assessment queue is full. Retry shortly.",null);}
        }
        return store.assessment(assessment.id());
    }
    void run(String id) {
        if(!store.markRunning(id)) return;
        AtomicReference<String> session=new AtomicReference<>();
        AtomicReference<List<EventSummary>> events=new AtomicReference<>(List.of());
        try {
            String result=skills.invoke("planTrip",new PlanningInput(id,store.snapshot(id).request()),view->{
                session.set(view.sessionId());
                events.set(view.events().stream().filter(e->Set.of("SKILL_STARTED","SKILL_FINISHED").contains(e.type()))
                    .map(e->new EventSummary(e.timestamp().toString(),e.type(),e.frameId(),e.route())).toList());
            });
            store.complete(id,json.readValue(result,ModelResult.class),session.get(),events.get());
        } catch(RuntimeException e) {
            log.warn("Assessment {} failed ({})",id,e.getClass().getSimpleName());
            log.debug("Assessment failure",e);
            store.fail(id,"Assessment could not produce a validated result. Check model configuration or Console diagnostics, then retry. No inventory was reserved.",session.get());
        }
    }
}
