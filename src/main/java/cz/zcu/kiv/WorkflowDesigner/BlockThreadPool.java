package cz.zcu.kiv.WorkflowDesigner;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class BlockThreadPool {

    private static Log logger = LogFactory.getLog(BlockThreadPool.class);
    private static final int POOL_SIZE = 1;   //pool size
    private static final int QUEUE_SIZE = 50; //task queue size

    private ThreadPoolExecutor blockTasksPool;
    private JSONObject jObject;
    private String outputFolder;
    private String workflowOutputFile;
    private List<Integer> wait;
    private Map<Integer,Block> blocks;
    private Workflow workflow;



    public BlockThreadPool(JSONObject jObject, String outputFolder, String workflowOutputFile, List<Integer> wait, Map<Integer,Block> blocks, Workflow workflow ){

        //ThreadFactory nameBlockThreadFactory = new ThreadFactoryBuilder().setNameFormat("pool-%d").build();

        this.blockTasksPool = new ThreadPoolExecutor(POOL_SIZE, POOL_SIZE, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(QUEUE_SIZE), new ThreadPoolExecutor.AbortPolicy());
        this.jObject = jObject;
        this.outputFolder = outputFolder;
        this.workflowOutputFile = workflowOutputFile;
        this.wait = wait;
        this.blocks = blocks;
        this.workflow = workflow;
    }

    /**
     * create ThreadPool
     */
    public boolean createBlocksThreadPool() throws InterruptedException, ExecutionException{
        logger.info("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^   Thread Pool   ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^:");
        logger.info("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Create a threadPool for all the blocks populated by the populateWaitList.");

        boolean error = false;
        List<Future<?>> futures = new ArrayList<>();

        for(int index = 0; index < wait.size(); index++){

           // Thread.sleep(1000);
            Integer aWait = wait.get(index);
            int waitBlockId = aWait;

            //submit a blockTask
            //return errorFlag in String"TRUE" or "FALSE"
            BlockThreadTask blockThreadTask = new BlockThreadTask (index, waitBlockId, blocks, jObject, outputFolder, workflowOutputFile, workflow);
            Future<?> future = submit(index, blockThreadTask);
            futures.add(future);

        }


        for(Future<?> future : futures){
            String errorFlag = (String) future.get();

            if(errorFlag.equals("TRUE")) {
                error = true;
                break;
            }
        }


        shutdown();

        return error;
    }


    public Future submit(int index, Callable blockThreadTask){
        logger.info("**************************************   ThreadPool Submit:");
        logger.info("**************************************   Call a Block Task thread "+index+" for Block execution.");

        return blockTasksPool.submit(blockThreadTask);

    }



    /**
     * execute block task
     * @param blockTask
     */
    public void executeTask(int index, Runnable blockTask) {
        logger.info("Execute a Block Task thread "+index+" for Block execution.");
        blockTasksPool.execute(blockTask);
    }

    /**
     *  shutdown
     */
    public void shutdown() {
        logger.info("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@  shutdown Pool... @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@   ");
        blockTasksPool.shutdown();

      //  try {
            if (!blockTasksPool.isTerminated()) {
                logger.info("1. shut down directly failed [" + blockTasksPool.toString() + "]");
                //blockTasksPool.awaitTermination(20, TimeUnit.SECONDS);

                if (blockTasksPool.isTerminated()) {
                    logger.info("2. Terminate success [" + blockTasksPool.toString() + "]");
                } else {
                    logger.info("3. [" + blockTasksPool.toString() + "] shut down failed，execute shutdownNow...");
                    if (blockTasksPool.shutdownNow().size() > 0) {
                        logger.info("4. [" + blockTasksPool.toString() + "] shun down failed");
                    } else {
                        logger.info("5. ShutdownNow() shut down success[" + blockTasksPool.toString() + "]");
                    }
                }
            } else {
                logger.info("6. Shutdown success[" + blockTasksPool.toString() + "]");
            }
//        } catch (InterruptedException e) {
//            logger.error("7. Interrupt. " + blockTasksPool.toString() + " stop.");
//        }
    }


}
