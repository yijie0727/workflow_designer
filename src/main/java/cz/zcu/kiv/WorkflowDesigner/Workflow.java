package cz.zcu.kiv.WorkflowDesigner;

import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockType;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.reflections.Reflections;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

/***********************************************************************************************************************
 *
 * This file is part of the Workflow Designer project

 * ==========================================
 *
 * Copyright (C) 2018 by University of West Bohemia (http://www.zcu.cz/en/)
 *
 ***********************************************************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 ***********************************************************************************************************************
 *
 * Workflow, 2018/17/05 6:32 Joey Pinto
 *
 * This file hosts the methods used to dynamically create the Javascript files needed for the workflow designer
 **********************************************************************************************************************/
public class Workflow {

    private String package_name;


    private static ArrayList<Block> block_definitions = null;

    public Workflow(String package_name){
        this.package_name = package_name;
    }

    /**
     * intializeBlocks - Joey Pinto
     *
     * This method intializes a directory made up of javascript files with all annotated blocktypes
     * @throws IOException - Exception if there is a problem creating directories
     */
    public  JSONArray initializeBlocks(String workflow_blocks_file) throws IOException {
        JSONArray blocks_array=new JSONArray();

        for(Block block:getBlockDefinitions()){
            //Write JS file description of block to array
            blocks_array.put(block.toJSON());
        }
        FileUtils.writeStringToFile(new File( workflow_blocks_file),blocks_array.toString(4));
        return blocks_array;
    }

    /**
     * getBlockDefinitions - Joey Pinto
     * This method creates a singleton access to block_defintions
     *
     * If not intialized, it searches for all classes with @BlockType annotations and gets the type and family
     * @return
     */
    public  ArrayList<Block> getBlockDefinitions(){
        if(block_definitions!=null) return block_definitions;
        else block_definitions = new ArrayList<>();
        Set<Class<?>> block_types = new Reflections(this.package_name).getTypesAnnotatedWith(BlockType.class);
        for(Class block_type:block_types){
            try {
                Block block= new Block(block_type.newInstance(),this);
                assert block!=null;
                Annotation annotation = block_type.getAnnotation(BlockType.class);
                Class<? extends Annotation> type = annotation.annotationType();
                String block_type_name=(String)type.getDeclaredMethod("type").invoke(annotation, (Object[])null);
                String block_type_family=(String)type.getDeclaredMethod("family").invoke(annotation, (Object[])null);
                block.setName(block_type_name);
                block.setFamily(block_type_family);
                block.initialize();
                block_definitions.add(block);

            } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        return block_definitions;
    }

    /**
     * getDefinition - Joey Pinto
     *
     * get the defintion of a block with it's type name
     * @param name
     * @return
     */
    public Block getDefinition(String name){
        for(Block block:getBlockDefinitions()){
            if(block.getName().equals(name)){
                return block;
            }
        }
        return null;
    }

    /**
     * indexBlocks - Joey Pinto
     * Index Blocks from JSONArray to HashMap
     * @param blocks_array
     * @return
     */
    public HashMap<Integer, Block> indexBlocks(JSONArray blocks_array) {
        HashMap<Integer,Block> blocks=new HashMap<>();
        for(int i=0; i<blocks_array.length(); i++){

            JSONObject block_object=blocks_array.getJSONObject(i);
            Block block = null;

            //get Block object by type of block in JSON
            Set<Class<?>> block_types = new Reflections(this.package_name).getTypesAnnotatedWith(BlockType.class);
            for(Class block_type:block_types){
                Annotation annotation = block_type.getAnnotation(BlockType.class);
                Class<? extends Annotation> type = annotation.annotationType();
                String block_type_name= null;
                try {
                    block_type_name = (String)type.getDeclaredMethod("type").invoke(annotation, (Object[])null);
                } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                    e.printStackTrace();
                }
                if (block_object.getString("type").equals(block_type_name)){
                    try {
                        block = new Block(block_type.newInstance(),this);
                        break;
                    } catch (InstantiationException | IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
            //Initialize the block I/O and configurations
            block.initialize();

            //Intitalize values from the JSONObject
            block.fromJSON(block_object);

            //Set reference ID of block
            blocks.put(block_object.getInt("id"),block);

        }
        return blocks;
    }

    /**
     * populateWaitList - Joey Pinto
     *
     * Populate wait list of blocks that are unprocessed
     * @param edges_array
     * @param blocks
     * @return
     */
    public ArrayList<Integer> populateWaitList(JSONArray edges_array, HashMap<Integer,Block>blocks){
        ArrayList<Integer>wait=new ArrayList<>();
        for(int i=0;i<edges_array.length();i++) {
            JSONObject edge_object = edges_array.getJSONObject(i);
            Block block1 = blocks.get(edge_object.getInt("block1"));
            Block block2 = blocks.get(edge_object.getInt("block2"));
            if(!block1.isProcessed()){
                if(block1.getInput()==null||block1.getInput().size()==0){
                    wait.add(edge_object.getInt("block1"));
                }
            }
            if(!block2.isProcessed()){
                if (block1.isProcessed() && !block2.isProcessed()) {
                    wait.add(edge_object.getInt("block2"));
                }
            }


        }
        return wait;
    }


    /**
     * execute - Joey Pinto
     *
     * process a workflow JSON Object
     *
     * @param jObject
     * @throws Exception
     */
    public void execute(JSONObject jObject) throws Exception{
        JSONArray blocks_array = jObject.getJSONArray("blocks");

        //Accumulate and index all blocks defined in the workflow
        HashMap<Integer,Block> blocks=indexBlocks(blocks_array);

        JSONArray edges_array = jObject.getJSONArray("edges");

        Block wait_block;
        while(true){
            //Populate wait list
            ArrayList<Integer>wait= populateWaitList(edges_array,blocks);

            //Wait queue is empty, exit
            if(wait.size()==0)break;

            //Process wait queue
            for (Integer aWait : wait) {
                boolean ready = true;
                int wait_block_id = aWait;
                wait_block = blocks.get(wait_block_id);

                HashMap<Integer, Block> dependencies = new HashMap<>();
                HashMap<String, String> source_param = new HashMap<>();
                HashMap<String, Integer> source_block = new HashMap<>();

                
                //Check dependencies of waiting block
                for (int i = 0; i < edges_array.length(); i++) {
                    JSONObject edge_object = edges_array.getJSONObject(i);

                    //Choose only edges that end on block 2
                    if (wait_block_id != edge_object.getInt("block2")) continue;

                    int block1_id = edge_object.getInt("block1");
                    Block block1 = blocks.get(block1_id);

                    //Populate the dependencies into the maps
                    populateDependencies(block1_id,block1,edge_object,dependencies,source_param,source_block);

                    //A dependency is unprocessed so not ready
                    if (!block1.isProcessed()) {
                        ready = false;
                        break;
                    }
                }

                if (ready) {
                    //Process the ready block
                    wait_block.processBlock(dependencies, source_block, source_param);
                    break;
                }

            }
        }
    }

    /**
     * populateDependencies - Joey Pinto
     *
     * Populate the dependency maps of a block
     *
     * @param block1_id
     * @param block1
     * @param edge_object
     * @param dependencies
     * @param source_param
     * @param source_block
     */
    private void populateDependencies(int block1_id, Block block1, JSONObject edge_object, HashMap<Integer,Block> dependencies, HashMap<String,String> source_param, HashMap<String,Integer> source_block) {
        JSONArray connector1 = edge_object.getJSONArray("connector1");
        JSONArray connector2 = edge_object.getJSONArray("connector2");

        for (int k = 0; k < connector1.length(); k++) {
            source_param.put(connector2.getString(k), connector1.getString(k));
            source_block.put(connector2.getString(k), block1_id);
        }

        dependencies.put(block1_id, block1);
    }


}
