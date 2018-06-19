package test;
import cz.zcu.kiv.WorkflowDesigner.FieldMismatchException;
import cz.zcu.kiv.WorkflowDesigner.Workflow;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;


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
 *
 * This test verifies the creation of all available blocks in the designer
 * The test.jar used for testing is the packaged version of the current project with its dependencies.
 **********************************************************************************************************************/
public class WorkflowDesignerTest {

    @Test
    public void testBlock() throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        JSONArray blocksArray=new Workflow(ClassLoader.getSystemClassLoader(),":test",null,"").initializeBlocks();
        assert blocksArray.length()==3;
    }

    @Test
    public void testJSONArithmetic() throws IOException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException, FieldMismatchException {

        String json = FileUtils.readFileToString(new File("test_data/test.json"),Charset.defaultCharset());
        JSONObject jsonObject = new JSONObject(json);
        JSONArray jsonArray = new Workflow(ClassLoader.getSystemClassLoader(), ":test",null,"").execute(jsonObject,"test_data");
        assert jsonArray !=null;
        assert jsonArray.length() == 3;
    }

    @Test
    public void testJSONSummation() throws IOException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException, FieldMismatchException {

        String json = "{\"edges\":[{\"id\":1,\"block1\":3,\"connector1\":[\"Operand\",\"output\"],\"block2\":2,\"connector2\":[\"Operand1\",\"input\",0]},{\"id\":2,\"block1\":1,\"connector1\":[\"Operand\",\"output\"],\"block2\":2,\"connector2\":[\"Operand1\",\"input\",0]}],\"blocks\":[{\"id\":1,\"x\":-277,\"y\":-223,\"type\":\"CONSTANT\",\"module\":\"workflow_test-1.0-jar-with-dependencies.jar:data\",\"values\":{\"Value\":\"3\"}},{\"id\":2,\"x\":119,\"y\":-180,\"type\":\"SUMMATION\",\"module\":\"workflow_test-1.0-jar-with-dependencies.jar:data\",\"values\":{}},{\"id\":3,\"x\":-202,\"y\":-116,\"type\":\"CONSTANT\",\"module\":\"workflow_test-1.0-jar-with-dependencies.jar:data\",\"values\":{\"Value\":\"5\"}}]}";
        JSONObject jsonObject = new JSONObject(json);
        JSONArray jsonArray = new Workflow(ClassLoader.getSystemClassLoader(), ":test",null,"").execute(jsonObject,"test_data");
        assert jsonArray !=null;
        assert jsonArray.length() == 3;
    }

}

