package cz.zcu.kiv.WorkflowDesigner;

import cz.zcu.kiv.WorkflowDesigner.Visualizations.PlotlyGraphs.Graph;
import cz.zcu.kiv.WorkflowDesigner.Visualizations.Table;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

public class BlockThreadTask implements Callable<String> {
    private static Log logger = LogFactory.getLog(BlockThreadTask.class);

    private int index;
    private int waitBlockId;
    private Map<Integer,Block> blocks;
    private JSONObject jObject;
    private String outputFolder;
    private String workflowOutputFile;
    private Workflow workflow;

    //private boolean error = false;
    private String errorFlag;

    public BlockThreadTask(int index, int waitBlockId, Map<Integer, Block> blocks, JSONObject jObject, String outputFolder, String workflowOutputFile, Workflow workflow) {
        this.index = index;
        this.waitBlockId = waitBlockId;
        this.blocks = blocks;
        this.jObject = jObject;
        this.outputFolder = outputFolder;
        this.workflowOutputFile = workflowOutputFile;
        this.workflow = workflow;
        this.errorFlag = "FALSE";
    }

    @Override
    public String call() throws Exception {
        logger.info("* Call Task: ");
        logger.info("* Call BlockTask Thread "+index+": "+ Thread.currentThread().getName());

        JSONArray edgesArray = this.jObject.getJSONArray("edges");
        JSONArray blocksArray = jObject.getJSONArray("blocks");

        Block waitBlock = blocks.get(waitBlockId);

        Map<Integer, Block> dependencies = new HashMap<>();
        Map<String,InputField>fields=new HashMap<>();

        //Check dependencies of waiting block
        for (int i = 0; i < edgesArray.length(); i++) {
            JSONObject edgeObject = edgesArray.getJSONObject(i);
            if (waitBlockId != edgeObject.getInt("block2")) continue;

            int block1Id = edgeObject.getInt("block1");
            Block block1 = blocks.get(block1Id);

            workflow.populateDependencies(block1Id,block1,edgeObject,dependencies,fields);

        }

        JSONObject block=Workflow.getBlockById(blocksArray,waitBlockId);
        logger.info("Processing block with ID "+waitBlockId);
        StringBuilder stdOutBuilder =new StringBuilder();
        StringBuilder stdErrBuilder =new StringBuilder();

        //Process the waiting block
        Object output = null;
        try {
            output = waitBlock.processBlock(dependencies, fields, stdOutBuilder, stdErrBuilder);
        }
        catch(Exception e){
            logger.error(e);
            errorFlag = "TRUE";
        }

        //update block JSONObject in the blockArray JSONArray
        synchronized(this) {

            if (errorFlag.equals("TRUE")) {
                block.put("error", true);
            } else {
                block.put("error", false);
            }

            //Assemble the output JSON
            JSONObject jsonObject = new JSONObject();
            if (output == null) {
                jsonObject = null;
            } else if (output.getClass().equals(String.class)) {
                jsonObject.put("type", "STRING");
                jsonObject.put("value", output);
            } else if (output.getClass().equals(File.class)) {
                File file = (File) output;
                String destinationFileName = "file_" + new Date().getTime() + "_" + file.getName();
                FileUtils.moveFile(file, new File(outputFolder + File.separator + destinationFileName));
                jsonObject.put("type", "FILE");
                JSONObject fileObject = new JSONObject();
                fileObject.put("title", file.getName());
                fileObject.put("filename", destinationFileName);
                jsonObject.put("value", fileObject);
            } else if (output.getClass().equals(Table.class)) {
                Table table = (Table) output;
                jsonObject.put("type", "TABLE");
                jsonObject.put("value", table.toJSON());
                File file = File.createTempFile("temp_", ".csv");
                FileUtils.writeStringToFile(file, table.toCSV(), Charset.defaultCharset());
                String destinationFileName = "table_" + new Date().getTime() + ".csv";
                FileUtils.moveFile(file, new File(outputFolder + File.separator + destinationFileName));
                JSONObject fileObject = new JSONObject();
                fileObject.put("title", destinationFileName);
                fileObject.put("filename", destinationFileName);
                jsonObject.put("value", fileObject);
            } else if (output.getClass().equals(Graph.class)) {
                Graph graph = (Graph) output;
                jsonObject.put("type", "GRAPH");
                jsonObject.put("value", graph.toJSON());
                File file = File.createTempFile("temp_", ".json");
                FileUtils.writeStringToFile(file, graph.toJSON().toString(4), Charset.defaultCharset());
                String destinationFileName = "graph_" + new Date().getTime() + ".json";
                FileUtils.moveFile(file, new File(outputFolder + File.separator + destinationFileName));
                JSONObject fileObject = new JSONObject();
                fileObject.put("title", destinationFileName);
                fileObject.put("filename", destinationFileName);
                jsonObject.put("value", fileObject);
            } else {
                jsonObject.put("type", "");
                jsonObject.put("value", output.toString());
            }

            if (jsonObject != null)
                block.put("output", jsonObject);
                block.put("stdout", stdOutBuilder.toString());
                block.put("stderr", stdErrBuilder.toString());
                block.put("completed", true);

            //Save Present state of output to file
            if (workflowOutputFile != null) {
                File workflowOutput = new File(workflowOutputFile);
                FileUtils.writeStringToFile(workflowOutput, blocksArray.toString(4), Charset.defaultCharset());
            }

        }

        return errorFlag;
    }
}
