package controllers;

import akka.actor.ActorSystem;
import javax.inject.*;
import play.*;
import play.mvc.*;
import java.util.concurrent.Executor;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import scala.concurrent.duration.Duration;
import scala.concurrent.ExecutionContextExecutor;

import views.html.*;
import java.io.File;
import java.sql.Timestamp;
import java.util.Date;


/**
 * This controller contains an action that demonstrates how to write
 * simple asynchronous code in a controller. It uses a timer to
 * asynchronously delay sending a response for 1 second.
 *
 * @param actorSystem We need the {@link ActorSystem}'s
 * {@link Scheduler} to run code after a delay.
 * @param exec We need a Java {@link Executor} to apply the result
 * of the {@link CompletableFuture} and a Scala
 * {@link ExecutionContext} so we can use the Akka {@link Scheduler}.
 * An {@link ExecutionContextExecutor} implements both interfaces.
 */
@Singleton
public class AsyncController extends Controller {

    private final ActorSystem actorSystem;
    private final ExecutionContextExecutor exec;

    @Inject
    public AsyncController(ActorSystem actorSystem, ExecutionContextExecutor exec) {
      this.actorSystem = actorSystem;
      this.exec = exec;
    }

    /**
     * An action that returns a plain text message after a delay
     * of 1 second.
     *
     * The configuration in the <code>routes</code> file means that this method
     * will be called when the application receives a <code>GET</code> request with
     * a path of <code>/message</code>.
     */
    public CompletionStage<Result> message() {
        return getFutureMessage(30, TimeUnit.SECONDS).thenApplyAsync(Results::ok, exec);
    }

    private CompletionStage<String> getFutureMessage(long time, TimeUnit timeUnit) {
        CompletableFuture<String> future = new CompletableFuture<>();
        actorSystem.scheduler().scheduleOnce(
            Duration.create(time, timeUnit),
            () -> future.complete("Your new application is ready."),
            exec
        );
        return future;
    }

    /**
     */
    public Result uploadBefore() {
        return ok(upload.render());
    }

    /**
     */
    //public Result upload() {
    public CompletionStage<Result> upload() {
     System.out.println("Start request ...");

     System.out.println(new Timestamp(new Date().getTime()));
        Http.MultipartFormData body = request().body().asMultipartFormData();
        Http.MultipartFormData.FilePart fileP = body.getFile("file");
     System.out.println("Start  job...");
     System.out.println(new Timestamp(new Date().getTime()));
        CompletionStage<Result> res =  uploadAndUpdate(1, TimeUnit.SECONDS, fileP).thenApplyAsync(Results::ok, exec);
     System.out.println("End request...");
     System.out.println(new Timestamp(new Date().getTime()));
        return res;
    }

    private CompletionStage<String> uploadAndUpdate(long time, 
                TimeUnit timeUnit, Http.MultipartFormData.FilePart fileP) {
        CompletableFuture<String> future = new CompletableFuture<>();
        actorSystem.scheduler().scheduleOnce(
            Duration.create(time, timeUnit),
            () ->  {
                if (fileP != null) {
                    File file = (File )fileP.getFile();
                    File tmpFile = new File("/tmp/" + fileP.getFilename());
                    file.renameTo(tmpFile);
                    try {
                        Thread.sleep(30000);
                        future.complete(tmpFile.getCanonicalPath()); 
                    } catch(Exception e) {
                        future.complete("no file path"); 
                    }
                } else {
                    future.complete("Upload Error"); 
                }
     System.out.println("End  job...");
     System.out.println(new Timestamp(new Date().getTime()));
            },
            exec
        );
        return future;
    }


}
