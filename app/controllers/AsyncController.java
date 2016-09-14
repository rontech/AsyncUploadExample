package controllers;

import akka.actor.ActorSystem;
import javax.inject.*;
import play.*;
import play.mvc.*;
import java.util.concurrent.Executor;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import scala.concurrent.Future;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;
import scala.concurrent.ExecutionContextExecutor;

import views.html.*;
import java.io.File;
import java.sql.Timestamp;
import java.util.Date;
import akka.actor.*;
import akka.util.Timeout;
import play.data.Form;
import static akka.pattern.Patterns.ask;
import akka.dispatch.*;
import actors.UpdateActor;


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
     * アップロード画面表示。
     */
    public Result uploadBefore() {
        return ok(upload.render());
    }

    /**
     * アップロード処理。<br>
     * <p>
     * １）通常のアップロード処理
     * ２）アップロード後、非同期更新処理ジョブ起動
     * </p>
     */
    public Result upload() {
        Http.MultipartFormData body = request().body().asMultipartFormData();
        Http.MultipartFormData.FilePart fileP = body.getFile("file");
        if (fileP != null) {
           //通常のアップロード処理
            File file = (File )fileP.getFile();
            File tmpFile = new File("/tmp/" + fileP.getFilename());
            file.renameTo(tmpFile);
 
            //非同期更新処理ジョブ起動
            try {
                ActorRef updateActor = actorSystem.actorOf(
                Props.create(UpdateActor.class), "001_" + tmpFile.getName());
                //startメッセージで実際な更新処理を起動する
                updateActor.tell("start:" + tmpFile.getName(), ActorRef.noSender());
                return ok(tmpFile.getName());
            } catch (Exception e) {
                e.printStackTrace();
                return ok("error");
            }
        }
        return ok("error");
    }

    /**
     * アップロードの後処理。
     */
    public Result uploadAfter() {
        final Timeout timeout = new Timeout(Duration.create(5, TimeUnit.SECONDS));
        String fileName =  request().getQueryString("file_name");
        ActorSelection selection = actorSystem.actorSelection("akka://application/user/001_" + fileName);
        try {
           ActorRef updateActor = Await.result(selection.resolveOne(timeout), timeout.duration());
            if(updateActor.isTerminated()) {
                return ok("completed");
            }
            //askでポーリング
            Future<Object> rt = ask(updateActor, "polling", timeout);
            String result = (String) Await.result(rt, timeout.duration());
            return ok("completed");
        } catch ( akka.actor.ActorNotFound anf) {
            return ok("completed");
        } catch (TimeoutException te) {
            return ok("timeout");
        } catch (Exception e) {
            e.printStackTrace();
            return ok("error");
        }
    }
}
