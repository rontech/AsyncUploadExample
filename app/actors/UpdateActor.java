package actors;

import akka.actor.*;

/**
 * Actors are implemented by extending the UntypeActor base class  and implementing the onReceive method. 
 * The onReceive method should define a series of case statements that defines which messages your Actor
 * can handle.
 */
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
