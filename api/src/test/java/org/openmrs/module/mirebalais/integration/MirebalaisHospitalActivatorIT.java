/*
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */

package org.openmrs.module.mirebalais.integration;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.Concept;
import org.openmrs.PatientIdentifierType;
import org.openmrs.api.context.Context;
import org.openmrs.module.addresshierarchy.AddressField;
import org.openmrs.module.addresshierarchy.AddressHierarchyLevel;
import org.openmrs.module.addresshierarchy.service.AddressHierarchyService;
import org.openmrs.module.idgen.AutoGenerationOption;
import org.openmrs.module.idgen.IdentifierPool;
import org.openmrs.module.idgen.RemoteIdentifierSource;
import org.openmrs.module.idgen.service.IdentifierSourceService;
import org.openmrs.module.metadatasharing.ImportedPackage;
import org.openmrs.module.metadatasharing.api.MetadataSharingService;
import org.openmrs.module.mirebalais.MirebalaisConstants;
import org.openmrs.module.mirebalais.MirebalaisGlobalProperties;
import org.openmrs.module.mirebalais.MirebalaisHospitalActivator;
import org.openmrs.module.mirebalais.api.MirebalaisHospitalService;
import org.openmrs.module.pacsintegration.PacsIntegrationGlobalProperties;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.openmrs.test.SkipBaseSetup;
import org.openmrs.validator.ValidateUtil;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;

@SkipBaseSetup
public class MirebalaisHospitalActivatorIT extends BaseModuleContextSensitiveTest {
	
	MirebalaisHospitalActivator activator;
	
	@Before
	public void beforeEachTest() throws Exception {
		initializeInMemoryDatabase();
		executeDataSet("requiredDataTestDataset.xml");
		executeDataSet("globalPropertiesTestDataset.xml");
		authenticate();
		activator = new MirebalaisHospitalActivator();
		activator.started();
		
	}
	
	@Test
	public void testThatActivatorDoesAllSetup() throws Exception {
		verifyMetadataPackagesConfigured(activator);
		verifyGlobalPropertiesConfigured();
		verifyPacsIntegrationGlobalPropertiesConfigured();
		verifyIdentifierSourcesConfigured();
		verifyAddressHierarchyLevelsCreated();
		verifyAddressHierarchyLoaded();
	}
	
	private void verifyMetadataPackagesConfigured(MirebalaisHospitalActivator activator) throws Exception {

        MetadataSharingService metadataSharingService = Context.getService(MetadataSharingService.class);

        // To catch the (common) case where someone gets the groupUuid wrong, we look for any installed packages that
        // we are not expecting
        Map<String, String> importedGroupUuids = new HashMap<String, String>();
        for (ImportedPackage importedPackage : metadataSharingService.getAllImportedPackages()) {
            importedGroupUuids.put(importedPackage.getGroupUuid(), importedPackage.getName());
        }
        for (Map.Entry<String, String> entry : importedGroupUuids.entrySet()) {
            if (!activator.getCurrentMetadataVersions().containsKey(entry.getKey())) {
                Assert.fail("Found a package with an unexpected groupUuid. Name: " + entry.getValue() + " , groupUuid: " + entry.getKey());
            }
        }

        for (Map.Entry<String, String> e : activator.getCurrentMetadataVersions().entrySet()) {
			String metadataPackageGroupUuid = e.getKey();
			String metadataPackageFilename = e.getValue();
			Integer expectedVersion = getMetadataPackageVersionFrom(metadataPackageFilename);
            ImportedPackage installedPackage = metadataSharingService.getImportedPackageByGroup(metadataPackageGroupUuid);
			Integer actualVersion = installedPackage == null ? null : installedPackage.getVersion();
			assertEquals("Failed to install " + metadataPackageFilename + ". Expected version: " + expectedVersion
                    + " Actual version: " + actualVersion, expectedVersion, actualVersion);
		}
		
		// Verify a few pieces of sentinel data that should have been in the packages
		Assert.assertNotNull(Context.getLocationService().getLocation("Mirebalais Hospital"));
		
		// this doesn't strictly belong here, but we include it as an extra sanity check on the MDS module
		for (Concept concept : Context.getConceptService().getAllConcepts()) {
			ValidateUtil.validate(concept);
		}
	}
	
	private Integer getMetadataPackageVersionFrom(String metadataPackageFilename) {
		Matcher matcher = Pattern.compile("\\w+-(\\d+).zip").matcher(metadataPackageFilename);
		matcher.matches();
		return Integer.valueOf(matcher.group(1));
	}
	
	private void verifyGlobalPropertiesConfigured() throws Exception {
		assertEquals(new Integer(8443), MirebalaisGlobalProperties.MIRTH_ADMIN_PORT());
		assertEquals(new Integer(6661), MirebalaisGlobalProperties.MIRTH_INPUT_PORT());
		assertEquals("/opt/mirthconnect", MirebalaisGlobalProperties.MIRTH_DIRECTORY());
		assertEquals("127.0.0.1", MirebalaisGlobalProperties.MIRTH_IP_ADDRESS());
		assertEquals("mirth", MirebalaisGlobalProperties.MIRTH_USERNAME());
		assertEquals("Mirth123", MirebalaisGlobalProperties.MIRTH_PASSWORD());
	}
	
	private void verifyPacsIntegrationGlobalPropertiesConfigured() throws Exception {
		assertEquals("admin", PacsIntegrationGlobalProperties.LISTENER_USERNAME());
		assertEquals("test", PacsIntegrationGlobalProperties.LISTENER_PASSWORD());
		assertEquals("7abcc666-7777-45e1-8c99-2b4f0c4f888a", PacsIntegrationGlobalProperties
                .RADIOLOGY_ORDER_TYPE_UUID());
	}
	
	private void verifyIdentifierSourcesConfigured() throws Exception {
		MirebalaisHospitalService service = Context.getService(MirebalaisHospitalService.class);
		IdentifierPool localZlIdentifierPool = service.getLocalZlIdentifierPool();
		RemoteIdentifierSource remoteZlIdentifierSource = service.getRemoteZlIdentifierSource();
		
		PatientIdentifierType zlIdentifierType = Context.getPatientService().getPatientIdentifierTypeByUuid(
		    MirebalaisConstants.ZL_IDENTIFIER_TYPE_UUID);
		AutoGenerationOption autoGenerationOption = Context.getService(IdentifierSourceService.class)
		        .getAutoGenerationOption(zlIdentifierType);
		
		assertEquals(MirebalaisConstants.ZL_IDENTIFIER_TYPE_UUID, zlIdentifierType.getUuid());
		assertEquals(zlIdentifierType, autoGenerationOption.getIdentifierType());
		assertEquals(localZlIdentifierPool, autoGenerationOption.getSource());
		
		assertEquals(MirebalaisConstants.LOCAL_ZL_IDENTIFIER_POOL_UUID, localZlIdentifierPool.getUuid());
		assertEquals(MirebalaisConstants.LOCAL_ZL_IDENTIFIER_POOL_BATCH_SIZE, localZlIdentifierPool.getBatchSize());
		assertEquals(MirebalaisConstants.LOCAL_ZL_IDENTIFIER_POOL_MIN_POOL_SIZE, localZlIdentifierPool
                .getMinPoolSize());
		
		assertEquals(MirebalaisConstants.REMOTE_ZL_IDENTIFIER_SOURCE_UUID, remoteZlIdentifierSource.getUuid());
		assertEquals("http://localhost", remoteZlIdentifierSource.getUrl());
        assertEquals("user_test", remoteZlIdentifierSource.getUser());
        assertEquals("abc123", remoteZlIdentifierSource.getPassword());

	}
	
	private void verifyAddressHierarchyLevelsCreated() throws Exception {
		AddressHierarchyService ahService = Context.getService(AddressHierarchyService.class);
		
		// assert that we now have six address hierarchy levels
		assertEquals(new Integer(6), ahService.getAddressHierarchyLevelsCount());
		
		// make sure they are mapped correctly
		List<AddressHierarchyLevel> levels = ahService.getOrderedAddressHierarchyLevels(true);
		assertEquals(AddressField.COUNTRY, levels.get(0).getAddressField());
		assertEquals(AddressField.STATE_PROVINCE, levels.get(1).getAddressField());
		assertEquals(AddressField.CITY_VILLAGE, levels.get(2).getAddressField());
		assertEquals(AddressField.ADDRESS_3, levels.get(3).getAddressField());
		assertEquals(AddressField.ADDRESS_1, levels.get(4).getAddressField());
		assertEquals(AddressField.ADDRESS_2, levels.get(5).getAddressField());
		
	}
	
	private void verifyAddressHierarchyLoaded() throws Exception {
		AddressHierarchyService ahService = Context.getService(AddressHierarchyService.class);
		
		// we should now have 26000+ address hierarchy entries
		Assert.assertTrue(ahService.getAddressHierarchyEntryCount() > 26000);
		
		assertEquals(1, ahService.getAddressHierarchyEntriesAtTopLevel().size());
		assertEquals("Haiti", ahService.getAddressHierarchyEntriesAtTopLevel().get(0).getName());
	}
	
}
