/**
* Copyright 2015 South London and Maudsley NHS Trust and King's College London
*
* Based on Wendy Chapman's NegEx algorithm, drawing on implementations by Imre
* Solti and Xiuyun Shen
*
* Licensed under the Apache License, Version 2.0 (the "License");
* 
* you may not use this file except in compliance with the License. You may
* obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
* WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
* License for the specific language governing permissions and limitations
* under the License.
*/



package gate.context.tests;

/**
 * Created by user on 10/28/2014.
 */
public interface ConstantsTest {
    String DEFAULT_NEGATION_VALUE = "Affirmed";
    String NEGATION_VALUE = "Negation";

    String DEFAULT_TEMPORALITY_VALUE = "Recent";
    String TEMPORALITY_VALUE = "Temporality";

    String DEFAULT_EXPERIENCER_VALUE = "Patient";
    String EXPERIENCER_VALUE = "Experiencer";

    String ANNOTATION_NAME_TRIGGER = "Trigger";
    String FEATURE_NAME_PSEUDO_TRIGGER = "pseudo-trigger";

}
