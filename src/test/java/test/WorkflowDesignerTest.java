package test;
import cz.zcu.kiv.WorkflowDesigner.BlockWorkFlow;
import cz.zcu.kiv.WorkflowDesigner.FieldMismatchException;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.util.HashMap;
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
 * WorkflowDesignerTest, 2019 GSoC19 P1  Yijie Huang
 *
 * This test verifies the creation of all available blocks in the designer
 * The test.jar used for testing is the packaged version of the current project with its dependencies.
 **********************************************************************************************************************/
public class WorkflowDesignerTest {

    @Test
    public void testBlock() throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        JSONArray blocksArray=new BlockWorkFlow(ClassLoader.getSystemClassLoader(),":test",null,"").initializeBlocks();
        assert blocksArray.length()==6;
    }

    @Test
    public void testJSONArithmeticObservation() throws IOException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException, FieldMismatchException, InterruptedException {

        String json = FileUtils.readFileToString(new File("test_data/test.json"),Charset.defaultCharset());
        JSONObject jsonObject = new JSONObject(json);
        File outputFile = File.createTempFile("JSONArithmetic_Test_",".json");
        outputFile.deleteOnExit();


        Map<Class, String> moduleSource = new HashMap<>();
        ArithmeticBlock A = new ArithmeticBlock();
        ConstantBlock B = new ConstantBlock();
        Class classA = A.getClass();
        Class classB = B.getClass();
        moduleSource.put(classA, ":test");
        moduleSource.put(classB, ":test");



        JSONArray jsonArray = new BlockWorkFlow(ClassLoader.getSystemClassLoader(), moduleSource,null,"test_data",1).execute(jsonObject,"test_data",outputFile.getAbsolutePath());

        assert jsonArray !=null;
        assert jsonArray.getJSONObject(0).getJSONObject("output").getInt("value")==15;
        assert jsonArray.length() == 3;
    }


    @Test
    public void testFileToStreamToFileObservation() throws IOException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException, FieldMismatchException, InterruptedException{
        String json = FileUtils.readFileToString(new File("test_data/FileToStreamTest.json"), Charset.defaultCharset());

        JSONObject jsonObject = new JSONObject(json);
        File outputFile = File.createTempFile("testFileToStreamToFile",".json");
        outputFile.deleteOnExit();

        Map<Class, String> moduleSource = new HashMap<>();
        FileToStream A = new FileToStream();
        StreamToFile B = new StreamToFile();
        Class classA = A.getClass();
        Class classB = B.getClass();
        moduleSource.put(classA, ":test");
        moduleSource.put(classB, ":test");

        JSONArray jsonArray = new BlockWorkFlow(ClassLoader.getSystemClassLoader(), moduleSource, null,"test_data",2).execute(jsonObject,"test_data",outputFile.getAbsolutePath());
        assert jsonArray !=null;
        assert jsonArray.length() == 2;
    }


}

