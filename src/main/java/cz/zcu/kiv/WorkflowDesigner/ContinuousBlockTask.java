package cz.zcu.kiv.WorkflowDesigner;

import cz.zcu.kiv.WorkflowDesigner.Visualizations.PlotlyGraphs.Graph;
import cz.zcu.kiv.WorkflowDesigner.Visualizations.Table;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
/***********************************************************************************************************************
 *
 * ContinuousBlockTask, GSoC 2019, 2019/05/06 Yijie Huang
 * Part of the methods are cited from the GSoC 2018 Block.java coded by Joey Pinto
 *
 * This file is the theadTask for each available Block to start its execution
 **********************************************************************************************************************/

public class ContinuousBlockTask implements Callable<Boolean> {

    private static Log logger = LogFactory.getLog(ContinuousBlockTask.class);

    private int waitBlockId;
    private ContinuousBlock waitBlock;
    private Map<Integer,ContinuousBlock> blocks;
    private ContinuousWorkflow continuousWorkflow;

    private JSONObject jObject;
    private String outputFolder;
    private String workflowOutputFile;


    public ContinuousBlockTask(int waitBlockId, ContinuousBlock waitBlock, Map<Integer, ContinuousBlock> blocks, ContinuousWorkflow continuousWorkflow, JSONObject jObject, String outputFolder, String workflowOutputFile) {
        this.waitBlockId = waitBlockId;
        this.waitBlock = waitBlock;
        this.blocks = blocks;
        this.continuousWorkflow = continuousWorkflow;
        this.jObject = jObject;
        this.outputFolder = outputFolder;
        this.workflowOutputFile = workflowOutputFile;
    }


    @Override
    public Boolean call() throws Exception {

        logger.info("                                                                                                                                *** Call BlockTask Thread for Block "+ waitBlock.getName() +", Block ID "+waitBlockId+": "+ Thread.currentThread().getName());

        JSONArray edgesArray = this.jObject.getJSONArray("edges");
        JSONArray blocksArray = jObject.getJSONArray("blocks");

        Map<Integer, ContinuousBlock> dependencies = new HashMap<>();
        Map<String,InputField>fields=new HashMap<>();


        //1. fetch dependencies of waiting block
        for (int i = 0; i < edgesArray.length(); i++) {
            JSONObject edgeObject = edgesArray.getJSONObject(i);
            if (waitBlockId != edgeObject.getInt("block2")) continue;

            int block1Id = edgeObject.getInt("block1");
            ContinuousBlock block1 = blocks.get(block1Id);

            continuousWorkflow.populateDependencies(block1Id,block1, edgeObject, dependencies, fields);
        }

        //2. start Block Execution
        StringBuilder stdOutBuilder =new StringBuilder();
        StringBuilder stdErrBuilder =new StringBuilder();
        boolean errorFlag = false;

        //3. Process the ready block
        Object output = null;
        try {
            output = waitBlock.processBlock(dependencies, fields, stdOutBuilder, stdErrBuilder);
        }
        catch(Exception e){
            logger.error(e);
            errorFlag = true;
        }

        //4. Assemble the output JSON
        JSONObject jsonObject = new JSONObject();
        if(output==null){
            jsonObject = null;
        }
        else if (output.getClass().equals(String.class)){
            jsonObject.put("type","STRING");
            jsonObject.put("value",output);
        }
        else if (output.getClass().equals(File.class)){
            File file = (File) output;
            String destinationFileName="file_"+new Date().getTime()+"_"+file.getName();
            FileUtils.moveFile(file,new File(outputFolder+File.separator+destinationFileName));
            jsonObject.put("type","FILE");
            JSONObject fileObject=new JSONObject();
            fileObject.put("title",file.getName());
            fileObject.put("filename",destinationFileName);
            jsonObject.put("value",fileObject);
        }
        else if (output.getClass().equals(Table.class)){
            Table table=(Table)output;
            jsonObject.put("type","TABLE");
            jsonObject.put("value",table.toJSON());
            File file =File.createTempFile("temp_",".csv");
            FileUtils.writeStringToFile(file,table.toCSV(), Charset.defaultCharset());
            String destinationFileName="table_"+new Date().getTime()+".csv";
            FileUtils.moveFile(file,new File(outputFolder+File.separator+destinationFileName));
            JSONObject fileObject=new JSONObject();
            fileObject.put("title",destinationFileName);
            fileObject.put("filename",destinationFileName);
            jsonObject.put("value",fileObject);
        }
        else if (output.getClass().equals(Graph.class)){
            Graph graph=(Graph)output;
            jsonObject.put("type","GRAPH");
            jsonObject.put("value",graph.toJSON());
            File file =File.createTempFile("temp_",".json");
            FileUtils.writeStringToFile(file,graph.toJSON().toString(4),Charset.defaultCharset());
            String destinationFileName="graph_"+new Date().getTime()+".json";
            FileUtils.moveFile(file,new File(outputFolder+File.separator+destinationFileName));
            JSONObject fileObject=new JSONObject();
            fileObject.put("title",destinationFileName);
            fileObject.put("filename",destinationFileName);
            jsonObject.put("value",fileObject);
        }
        else{
            jsonObject.put("type","");
            jsonObject.put("value",output.toString());
        }

        //5. update the Block JSON
        JSONObject block = ContinuousWorkflow.getBlockById(blocksArray, waitBlockId);
        synchronized(blocksArray){
            if (errorFlag) {
                block.put("error", true);
            } else {
                block.put("error", false);
            }
            if (jsonObject != null)
                block.put("output", jsonObject);
                block.put("stdout", stdOutBuilder.toString());
                block.put("stderr", stdErrBuilder.toString());
                block.put("completed", true);

            //When this continuous workFlow is totally finished, save present state of output to file --> update job_idNumbers.json(only update it once)
            if(workflowOutputFile!=null){
                File workflowOutput=new File(workflowOutputFile);
                FileUtils.writeStringToFile(workflowOutput,blocksArray.toString(4), Charset.defaultCharset());
            }

        }


        waitBlock.setOutputPrepared(true);
        waitBlock.setSentDataCount(0);
        logger.info("                                                                                                                                # SET OutputPrepared to true, UPDATE sentDataCount = 0,  for Block "+ waitBlock.getName() +", Block D "+waitBlockId+": "+ Thread.currentThread().getName());

        //6. Use CompletionService to fetch the error Flag of the blocked queue. (Only the completed task results will be put into the queue.)
        return errorFlag;
    }
}
