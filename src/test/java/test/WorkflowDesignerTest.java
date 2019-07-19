package test;
import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockType;
import cz.zcu.kiv.WorkflowDesigner.BlockWorkFlow;
import cz.zcu.kiv.WorkflowDesigner.FieldMismatchException;
import cz.zcu.kiv.WorkflowDesigner.WrongTypeException;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


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
 * WorkflowDesignerTest, 2018/17/05 6:32 Joey Pinto
 * WorkflowDesignerTest, 2019 GSoC19 P1 P2 Yijie Huang
 *
 * This test verifies the creation of all available blocks in the designer
 * The test.jar used for testing is the packaged version of the current project with its dependencies.
 **********************************************************************************************************************/
public class WorkflowDesignerTest {

    @Test
    public void testBlock() throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        JSONArray blocksArray=new BlockWorkFlow(ClassLoader.getSystemClassLoader(),":test",null,"").initializeBlocks();
        assert blocksArray.length()==11;
    }

    @Test
    public void testJSONArithmeticObservation() throws WrongTypeException, IOException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException, FieldMismatchException, InterruptedException {

        String json = FileUtils.readFileToString(new File("test_data/test.json"),Charset.defaultCharset());
        JSONObject jsonObject = new JSONObject(json);
        File outputFile = File.createTempFile("JSONArithmetic_Test_",".json");
        outputFile.deleteOnExit();

        JSONArray blocksArray = jsonObject.getJSONArray("blocks");
        List<String> blockTypes = new ArrayList<>();
        for (int i = 0; i < blocksArray.length(); i++) {
            JSONObject blockObject = blocksArray.getJSONObject(i);
            blockTypes.add(blockObject.getString("type"));
        }
        Map<Class, String> moduleSource = new HashMap<>();
        Pack.assignModuleSource(moduleSource, blockTypes);


        JSONArray jsonArray = new BlockWorkFlow(ClassLoader.getSystemClassLoader(), moduleSource,null,"test_data",1).execute(jsonObject,"test_data",outputFile.getAbsolutePath());

        assert jsonArray !=null;
        assert jsonArray.getJSONObject(0).getJSONObject("output").getInt("value")==15;
        assert jsonArray.length() == 3;
    }


    @Test
    public void testFileToStreamToFileObservation() throws WrongTypeException, IOException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException, FieldMismatchException, InterruptedException{
        String json = FileUtils.readFileToString(new File("test_data/FileToStreamTest.json"), Charset.defaultCharset());

        JSONObject jsonObject = new JSONObject(json);
        File outputFile = File.createTempFile("testFileToStreamToFile",".json");
        outputFile.deleteOnExit();

        JSONArray blocksArray = jsonObject.getJSONArray("blocks");
        List<String> blockTypes = new ArrayList<>();
        for (int i = 0; i < blocksArray.length(); i++) {
            JSONObject blockObject = blocksArray.getJSONObject(i);
            blockTypes.add(blockObject.getString("type"));
        }
        Map<Class, String> moduleSource = new HashMap<>();
        Pack.assignModuleSource(moduleSource, blockTypes);


        JSONArray jsonArray = new BlockWorkFlow(ClassLoader.getSystemClassLoader(), moduleSource, null,"test_data",2).execute(jsonObject,"test_data",outputFile.getAbsolutePath());
        assert jsonArray !=null;
        assert jsonArray.length() == 2;
    }


    @Test
    public void testContinuous1() throws WrongTypeException, IOException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException, FieldMismatchException, InterruptedException{
        String json = FileUtils.readFileToString(new File("test_data/pipedStreamTest.json"), Charset.defaultCharset());

        JSONObject jsonObject = new JSONObject(json);
        File outputFile = File.createTempFile("testBlockPipeStream",".json");
        outputFile.deleteOnExit();

        JSONArray blocksArray = jsonObject.getJSONArray("blocks");
        List<String> blockTypes = new ArrayList<>();
        for (int i = 0; i < blocksArray.length(); i++) {
            JSONObject blockObject = blocksArray.getJSONObject(i);
            blockTypes.add(blockObject.getString("type"));
        }
        Map<Class, String> moduleSource = new HashMap<>();
        Pack.assignModuleSource(moduleSource, blockTypes);


        JSONArray jsonArray = new BlockWorkFlow(ClassLoader.getSystemClassLoader(), moduleSource, null,"test_data",3).
                execute(  jsonObject,  "test_data",  outputFile.getAbsolutePath());
        assert jsonArray !=null;
        assert jsonArray.length() == 4;
    }


    @Test
    public void testContinuous2() throws WrongTypeException,  IOException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException, FieldMismatchException, InterruptedException{
        String json = FileUtils.readFileToString(new File("test_data/pipeTest2.json"), Charset.defaultCharset());

        JSONObject jsonObject = new JSONObject(json);
        File outputFile = File.createTempFile("testBlockPipeStream",".json");
        outputFile.deleteOnExit();

        JSONArray blocksArray = jsonObject.getJSONArray("blocks");
        List<String> blockTypes = new ArrayList<>();
        for (int i = 0; i < blocksArray.length(); i++) {
            JSONObject blockObject = blocksArray.getJSONObject(i);
            blockTypes.add(blockObject.getString("type"));
        }
        Map<Class, String> moduleSource = new HashMap<>();
        Pack.assignModuleSource(moduleSource, blockTypes);


        JSONArray jsonArray = new BlockWorkFlow(ClassLoader.getSystemClassLoader(), moduleSource, null,"test_data",4).
                execute(  jsonObject,  "test_data",  outputFile.getAbsolutePath());
        assert jsonArray !=null;
        assert jsonArray.length() == 2;
    }


    //@Test
    public void testPack() throws IOException, NoSuchMethodException, InvocationTargetException, IllegalAccessException{

        String json=FileUtils.readFileToString(new File("test_data/test.json"), Charset.defaultCharset());
        JSONObject jsonObject = new JSONObject(json);
        JSONArray blocksArray = jsonObject.getJSONArray("blocks");
        List<String> blockTypes = new ArrayList<>();
        for(int i = 0; i<blocksArray.length(); i++){
            JSONObject blockObject = blocksArray.getJSONObject(i);
            blockTypes.add(blockObject.getString("type"));
        }

        String module = ":test";
        Map<Class, String> moduleSource = new HashMap<>();
        List<Class<?>> classesList = Pack.getClassesFromPackage("test");
        for(Class blockClass : classesList){

            Annotation annotation = blockClass.getAnnotation(BlockType.class);
            if(annotation == null) continue;
            Class<? extends Annotation> blockType = annotation.annotationType();
            String blockTypeName = (String)blockType.getDeclaredMethod("type").invoke(annotation);

            for(String blockTypeStr :blockTypes){
                if(blockTypeName.equals(blockTypeStr)){
                    moduleSource.put(blockClass, module);
                    break;
                }
            }
        }
        System.out.println(moduleSource.keySet());
    }

}

