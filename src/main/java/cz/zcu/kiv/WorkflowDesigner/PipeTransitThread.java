package cz.zcu.kiv.WorkflowDesigner;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.List;

/**
 * PipeTransitThread - GSoC 2019 P2 Yijie Huang
 * This class is used for each block's output, to send data to all its next destination blocks
 */
public class PipeTransitThread implements Runnable {
    private static Log logger = LogFactory.getLog(PipeTransitThread.class);

    private int id;             //source block id
    private String outputName;  //one of the outputs name of this source block
    private int b = 0;

    private PipedInputStream pipedInTransit;            //connect to one of the source block's output (pipedOutputStream),  to read data from the output
    private List<PipedOutputStream> pipedOutTransitsList;   //connect to all its destination blocks' inputs (pipedInputStream), to write data (b) to all its destination inputs of different blocks


    public PipeTransitThread(int id, String outputName, PipedInputStream pipedInTransit, List<PipedOutputStream> pipedOutTransitsList) {
        this.id = id;
        this.outputName = outputName;
        this.pipedInTransit = pipedInTransit;
        this.pipedOutTransitsList = pipedOutTransitsList;
    }

    @Override
    public void run() {
        logger.info("block id: "+id+", output: "+outputName+", pipedInTransit reads this output's data, use pipedOutTransit to write byte to all its destination inputs.");

        try{

            while ((b = pipedInTransit.read()) != -1) {

                for(PipedOutputStream pipedOutTransit:  pipedOutTransitsList){
                    pipedOutTransit.write(b);
                }
            }


            pipedInTransit.close();
            for(PipedOutputStream pipedOutTransit:  pipedOutTransitsList){
                pipedOutTransit.close();
            }



        } catch (IOException e){
            logger.error("block id: "+id+", output: "+outputName+", pipedTransit IO error.");
        }
    }


}
