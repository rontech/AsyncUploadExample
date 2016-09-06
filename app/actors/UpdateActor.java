package actors;

import akka.actor.*;

public class UpdateActor extends UntypedActor {
    @Override
    public void onReceive(Object msg) throws Exception {
        if(msg instanceof String) {
            if (((String)msg).startsWith("start:") ) {
                System.out.println("Start the Job!");
                Thread.sleep(32000);
                sender().tell("Completed! " , self());
                System.out.println("End the Job!");
            }
            if (((String)msg).equals("polling") ) {
                System.out.println("Polling received!");
                sender().tell("Completed! " , self());
                getContext().stop(getSelf());
            }
        }
    }
}
