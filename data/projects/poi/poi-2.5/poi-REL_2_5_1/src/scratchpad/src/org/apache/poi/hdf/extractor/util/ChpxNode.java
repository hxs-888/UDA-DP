
/* ====================================================================
   Copyright 2002-2004   Apache Software Foundation

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
==================================================================== */
        


package org.apache.poi.hdf.extractor.util;


/**
 * Comment me
 *
 * @author Ryan Ackley 
 */

public class ChpxNode extends PropertyNode
{


  public ChpxNode(int fcStart, int fcEnd, byte[] chpx)
  {
    super(fcStart, fcEnd, chpx);
  }
  public byte[] getChpx()
  {
    return super.getGrpprl();
  }

}
