## このサンプルについて
れいの有名なherokuタイムアウト問題**[Heroku Timeout]**の対策です。
play frameworkを用いての開発でどうしてもロング処理が必要な場合はこのサンプルを参考にしてください。

## 基本的な考え方

通常な非同期処理ではブラウザとherokuルータ間のコネクションが切断されないため、タイムアウトが起こります。
このサンプルはアップロード処理自身のタイムアウト対応でなく、アップロードの後処理がロング処理である場合は
タイムアウトを回避することができます。アップロードの後処理は**[Akka]**のactorを使用して、非同期ジョブとして
処理します。

## ブラウザ側処理

Ajaxの非同期呼び出しを使用します。

app/views/upload.scala.html
```javascript
$('#submit').click(function (event) {
   event.preventDefault();
   var file = $('#file').get(0).files[0];
   var formData = new FormData();
   var fileName = null;
   formData.append('file', file);
   $("#uploading").css("display","block");
   var res = $.ajax({
       url: 'upload',
       data: formData,
       type: 'POST',
       contentType: false,
       processData: false,
       beforeSend: function (data) {
         alert('Are you sure you want to upload document?');
       }
   }).done(
      function(data) {
        $("#uploading").css("display","none");
        fileName = data;
        alert('Upload completed: ' + data);
        if( fileName != "error" ) {
          $("#updating").css("display","block");
          polling(fileName);
        }
      }
   ).fail(
    function (jqXHR, textStatus, errorThrown) {
      $("#uploading").css("display","none");
      alert(textStatus + ': ' + errorThrown);
    }
   );
  return false;
});
```
uploadのdone処理でポーリングを行っています。

## サーバ側処理

upload処理は通常に行います。
```java
        Http.MultipartFormData body = request().body().asMultipartFormData();
        Http.MultipartFormData.FilePart fileP = body.getFile("file");
        if (fileP != null) {
           //通常のアップロード処理
            File file = (File )fileP.getFile();
            File tmpFile = new File("/tmp/" + fileP.getFilename());
            file.renameTo(tmpFile);
```

upload処理後、ジョブを起動します。
```java
                ActorRef updateActor = actorSystem.actorOf(
                Props.create(UpdateActor.class), "001_" + tmpFile.getName());
                //startメッセージで実際な更新処理を起動する
                updateActor.tell("start:" + tmpFile.getName(), ActorRef.noSender());
```

ジョブの処理状態は新たなリクエストで取得します。
```java
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
```

## Actorの実装について

sleep処理はロング処理を示しています。
```java
public class UpdateActor extends UntypedActor {
    @Override
    public void onReceive(Object msg) throws Exception {
        if(msg instanceof String) {
            //start処理(block mode)
            if (((String)msg).startsWith("start:") ) {
                System.out.println("Start the Job!");
                Thread.sleep(32000);
                sender().tell("Completed! " , self());
                System.out.println("End the Job!");
            }

            //pollingのメッセーイを受けたらactorを終了する
            if (((String)msg).equals("polling") ) {
                System.out.println("Polling received!");
                sender().tell("Completed! " , self());
                getContext().stop(getSelf());
            }
        }
    }
}
```

[Heroku Timeout]: https://devcenter.heroku.com/articles/request-timeout
[Akka]: http://akka.io
