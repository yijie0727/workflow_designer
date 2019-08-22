package cz.zcu.kiv.WorkflowDesigner;

import com.sun.org.apache.xpath.internal.operations.Bool;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cz.zcu.kiv.WorkflowDesigner.BlockObservation.MIX;

/**
 * PipeTransitThread - GSoC 2019 P2 Yijie Huang
 * This class is used for each block's output, to send data to all its next destination blocks
 */
public class PipeTransitThread implements Runnable {
    private static Log logger = LogFactory.getLog(PipeTransitThread.class);

    private BlockObservation block;             //source block id
    private String outputName;  //one of the outputs name of this source block

    private PipedInputStream pipedInTransit;                //connect to one of the source block's output (pipedOutputStream),  to read data from the output
    private List<PipedOutputStream> pipedOutTransitsList;   //connect to all its destination blocks' inputs (pipedInputStream), to write data (b) to all its destination inputs of different blocks


    public PipeTransitThread(BlockObservation block, String outputName, PipedInputStream pipedInTransit, List<PipedOutputStream> pipedOutTransitsList) {
        this.block = block;
        this.outputName = outputName;
        this.pipedInTransit = pipedInTransit;
        this.pipedOutTransitsList = pipedOutTransitsList;
    }

    @Override
    public void run() {
        logger.info(" block id: "+block.getId()+", output: "+outputName+", pipedInTransit reads this output's data, use pipedOutTransit to write byte to all its destination inputs.");

        try{
            boolean MIXFlag = false;
            for(BlockObservation destBlock: block.getDestinationObservers()){
                if ( destBlock.getBlockModel() == MIX ) {
                    MIXFlag = true;
                    break;
                }
            }

            // if some of this block's destination blocks are MIX
            if(MIXFlag){
                int random = (int)(Math.random()*100000);
                String fileName = random+"_JID"+block.getJobID()+"_ID"+block.getId()+" "+block.getName()+" "+outputName+"_";

                File tmpFile = File.createTempFile(fileName, ".tmp");

                FileOutputStream fileOut = new FileOutputStream(tmpFile);

                byte[] bytes= new byte[1];
                while(pipedInTransit.read(bytes)!=-1){
                    fileOut.write(bytes);
                    fileOut.flush();
                }
                fileOut.close();
                logger.info(" block id: "+block.getId()+", output: "+outputName+"all ready store data to file to avoid block");

                //check all the sourceBlocks of its Destination Block is Complete
                for(BlockObservation destBlock: block.getDestinationObservers()){
                   if ( destBlock.checkSourcePrepared() ) {
                       logger.error("source block error"); return;
                   }
                }

                logger.info(" block id: "+block.getId()+", output: "+outputName+"destination prepared, ready to send stream");
                FileInputStream fileIn = new FileInputStream(tmpFile);
                byte[] bytes2= new byte[1];
                while(fileIn.read(bytes2)!=-1){
                    for(PipedOutputStream pipedOutTransit:  pipedOutTransitsList){
                        pipedOutTransit.write(bytes2);
                    }
                }
                fileIn.close();

                tmpFile.deleteOnExit();
            }

            // All of this blocks'destinations are PIPE
            else {
                int b = 0;
                while ((b = pipedInTransit.read()) != -1) {

                    for(PipedOutputStream pipedOutTransit:  pipedOutTransitsList){
                        pipedOutTransit.write(b);
                    }
                }
            }


            pipedInTransit.close();
            for(PipedOutputStream pipedOutTransit:  pipedOutTransitsList){
                pipedOutTransit.close();
            }

            logger.info("block id: "+block.getId()+", output: "+outputName+", transit stream through PIPE completely");

        } catch (IOException e){
            logger.error("block id: "+block.getId()+", output: "+outputName+" error: " + e);
        }
    }


}
