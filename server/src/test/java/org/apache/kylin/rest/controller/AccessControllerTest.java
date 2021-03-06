/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package org.apache.kylin.rest.controller;

import org.apache.kylin.cube.CubeInstance;
import org.apache.kylin.metadata.project.ProjectInstance;
import org.apache.kylin.rest.request.AccessRequest;
import org.apache.kylin.rest.response.AccessEntryResponse;
import org.apache.kylin.rest.security.AclEntityType;
import org.apache.kylin.rest.security.AclPermissionType;
import org.apache.kylin.rest.service.AccessService;
import org.apache.kylin.rest.service.CubeService;
import org.apache.kylin.rest.service.ProjectService;
import org.apache.kylin.rest.service.ServiceTestBase;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author xduo
 */
public class AccessControllerTest extends ServiceTestBase implements AclEntityType, AclPermissionType {

    private AccessController accessController;

    private CubeController cubeController;

    private ProjectController projectController;

    private String MODELER = "MODELER";

    private String ANALYST = "ANALYST";

    private String ADMIN = "ADMIN";

    @Autowired
    @Qualifier("projectService")
    ProjectService projectService;

    @Autowired
    @Qualifier("cubeMgmtService")
    CubeService cubeService;

    @Autowired
    @Qualifier("accessService")
    AccessService accessService;

    @Before
    public void setup() throws Exception {
        super.setup();
        accessController = new AccessController();
        accessController.setAccessService(accessService);
        cubeController = new CubeController();
        cubeController.setCubeService(cubeService);
        projectController = new ProjectController();
        projectController.setProjectService(projectService);
    }

    @Test
    public void testBasics() throws IOException {
        swichToAdmin();
        List<AccessEntryResponse> aes = accessController.getAccessEntities(CUBE_INSTANCE, "a24ca905-1fc6-4f67-985c-38fa5aeafd92");
        Assert.assertTrue(aes.size() == 0);

        AccessRequest accessRequest = getAccessRequest(MODELER, ADMINISTRATION);
        aes = accessController.grant(CUBE_INSTANCE, "a24ca905-1fc6-4f67-985c-38fa5aeafd92", accessRequest);
        Assert.assertTrue(aes.size() == 1);

        Long aeId = null;
        for (AccessEntryResponse ae : aes) {
            aeId = (Long) ae.getId();
        }
        Assert.assertNotNull(aeId);

        accessRequest = new AccessRequest();
        accessRequest.setAccessEntryId(aeId);
        accessRequest.setPermission(READ);

        aes = accessController.update(CUBE_INSTANCE, "a24ca905-1fc6-4f67-985c-38fa5aeafd92", accessRequest);
        Assert.assertTrue(aes.size() == 1);
        for (AccessEntryResponse ae : aes) {
            aeId = (Long) ae.getId();
        }
        Assert.assertNotNull(aeId);

        accessRequest = new AccessRequest();
        accessRequest.setAccessEntryId(aeId);
        accessRequest.setPermission(READ);
        aes = accessController.revoke(CUBE_INSTANCE, "a24ca905-1fc6-4f67-985c-38fa5aeafd92", accessRequest);
        assertEquals(0, aes.size());

    }

    @Test
    public void testAuthInProjectLevel() throws Exception {
        List<AccessEntryResponse> aes = null;
        swichToAdmin();
        List<ProjectInstance> projects = projectController.getProjects(10000, 0);
        assertTrue(projects.size() > 0);
        ProjectInstance project = projects.get(0);
        swichToAnalyst();
        projects = projectController.getProjects(10000, 0);
        assertEquals(0, projects.size());
        //grant auth in project level
        swichToAdmin();
        aes = accessController.grant(PROJECT_INSTANCE, project.getUuid(), getAccessRequest(ANALYST, READ));
        swichToAnalyst();
        projects = projectController.getProjects(10000, 0);
        assertEquals(1, projects.size());

        //revoke auth
        swichToAdmin();
        AccessRequest request = getAccessRequest(ANALYST, READ);
        request.setAccessEntryId((Long) aes.get(0).getId());
        accessController.revoke(PROJECT_INSTANCE, project.getUuid(), request);
        swichToAnalyst();
        projects = projectController.getProjects(10000, 0);
        assertEquals(0, projects.size());
    }

    @Test
    public void testAuthInCubeLevel() throws Exception {
        swichToAdmin();
        List<CubeInstance> cubes = cubeController.getCubes(null, null, null, 100000, 0);
        assertTrue(cubes.size() > 0);
        CubeInstance cube = cubes.get(0);
        swichToAnalyst();
        cubes = cubeController.getCubes(null, null, null, 100000, 0);
        assertTrue(cubes.size() == 0);

        //grant auth
        AccessRequest accessRequest = getAccessRequest(ANALYST, READ);
        try {
            accessController.grant(CUBE_INSTANCE, cube.getUuid(), accessRequest);
            fail("ANALYST should not have auth to grant");
        } catch (AccessDeniedException e) {
            //correct
        }
        swichToAdmin();
        List<AccessEntryResponse> aes = accessController.grant(CUBE_INSTANCE, cube.getUuid(), accessRequest);
        Assert.assertTrue(aes.size() == 1);
        swichToAnalyst();
        cubes = cubeController.getCubes(null, null, null, 100000, 0);
        assertEquals(1, cubes.size());

        //revoke auth
        try {
            accessController.revoke(CUBE_INSTANCE, cube.getUuid(), accessRequest);
            fail("ANALYST should not have auth to revoke");
        } catch (AccessDeniedException e) {
            //correct
        }
        swichToAdmin();
        accessRequest.setAccessEntryId((Long) aes.get(0).getId());
        accessController.revoke(CUBE_INSTANCE, cube.getUuid(), accessRequest);
        swichToAnalyst();
        cubes = cubeController.getCubes(null, null, null, 10000, 0);
        assertEquals(0, cubes.size());
    }

    private void swichToAdmin() {
        Authentication adminAuth = new TestingAuthenticationToken("ADMIN", "ADMIN", "ROLE_ADMIN");
        SecurityContextHolder.getContext().setAuthentication(adminAuth);
    }

    private void swichToAnalyst() {
        Authentication analystAuth = new TestingAuthenticationToken("ANALYST", "ANALYST", "ROLE_ANALYST");
        SecurityContextHolder.getContext().setAuthentication(analystAuth);
    }

    private AccessRequest getAccessRequest(String role, String permission) {
        AccessRequest accessRequest = new AccessRequest();
        accessRequest.setPermission(permission);
        accessRequest.setSid(role);
        accessRequest.setPrincipal(true);
        return accessRequest;
    }
}
