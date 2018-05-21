package test;
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
 **********************************************************************************************************************/
public class WorkflowDesignerTest {

    @Test
    public void testBlock() throws IOException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        JSONArray blocks_array=new Workflow("test").initializeBlocks();
        FileUtils.writeStringToFile(new File("workflow_designer/workflow_blocks.json"),blocks_array.toString(4),Charset.defaultCharset());
    }

    @Test
    public void testJSON() throws IOException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {

        String json = FileUtils.readFileToString(new File("workflow_designer/test.json"),Charset.defaultCharset());
        JSONObject jsonObject = new JSONObject(json);
        new Workflow("test").execute(jsonObject);
        assert  ArithmeticBlock.getOp3()==15;
    }



}

