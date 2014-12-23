/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mastfrog.scamper.queries;

import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.scamper.ProtocolModule;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@TestWith(ProtocolModule.class)
public class IdsTest {

    @Test
    public void test(Ids ids) {
        long[] found = new long[50];
        for (int i = 0; i < found.length; i++) {
            long val = ids.next();
            found[i] = val;
            assertTrue("No match on " + val, ids.isValid(val));
        }
    }
}
