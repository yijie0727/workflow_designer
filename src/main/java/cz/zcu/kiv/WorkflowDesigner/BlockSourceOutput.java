package cz.zcu.kiv.WorkflowDesigner;

/***********************************************************************************************************************
 *
 * This file is part of the Workflow Designer project

 * ==========================================
 *
 * Copyright (C) 2019 by University of West Bohemia (http://www.zcu.cz/en/)
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
 * BlockSourceOutput, 2019 GSoC P1 Yijie Huang
 *
 * This file is used for the value of IOMap of each block to collect all the source blocks of one destination block
 **********************************************************************************************************************/

public class BlockSourceOutput {

    private int sourceBlockID;
    private BlockObservation blockObservation;
    private String sourceParam; //The name of the Output in the source Block

    public BlockSourceOutput(){}

    public BlockSourceOutput(int sourceBlockID, BlockObservation blockObservation, String sourceParam) {
        this.sourceBlockID = sourceBlockID;
        this.blockObservation = blockObservation;
        this.sourceParam = sourceParam;
    }

    public int getSourceBlockID() {
        return sourceBlockID;
    }

    public void setSourceBlockID(int sourceBlockID) {
        this.sourceBlockID = sourceBlockID;
    }

    public BlockObservation getBlockObservation() {
        return blockObservation;
    }

    public void setBlockObservation(BlockObservation blockObservation) {
        this.blockObservation = blockObservation;
    }

    public String getSourceParam() {
        return sourceParam;
    }

    public void setSourceParam(String sourceParam) {
        this.sourceParam = sourceParam;
    }
}
