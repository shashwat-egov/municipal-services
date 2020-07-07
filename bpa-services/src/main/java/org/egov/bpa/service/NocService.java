package org.egov.bpa.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.egov.bpa.config.BPAConfiguration;
import org.egov.bpa.repository.ServiceRequestRepository;
import org.egov.bpa.util.BPAConstants;
import org.egov.bpa.web.model.BPA;
import org.egov.bpa.web.model.BPARequest;
import org.egov.bpa.web.model.RequestInfoWrapper;
import org.egov.bpa.web.model.NOC.Noc;
import org.egov.bpa.web.model.NOC.NocRequest;
import org.egov.bpa.web.model.NOC.NocResponse;
import org.egov.bpa.web.model.NOC.Workflow;
import org.egov.bpa.web.model.NOC.enums.ApplicationType;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.encoder.org.apache.commons.lang.StringUtils;

@Service
@Slf4j
public class NocService {

	@Autowired
	private EDCRService edcrService;

	@Autowired
	private BPAConfiguration config;

	@Autowired
	private ServiceRequestRepository serviceRequestRepository;

	@Autowired
	private ObjectMapper mapper;

	@SuppressWarnings("unchecked")
	public void createNocRequest(BPARequest bpaRequest, Object mdmsData) {
		BPA bpa = bpaRequest.getBPA();
		Map<String, String> edcrResponse = edcrService.getEDCRDetails(bpaRequest.getRequestInfo(), bpaRequest.getBPA());
		log.info("applicationType in NOC is " + edcrResponse.get(BPAConstants.APPLICATIONTYPE));
		log.info("serviceType in NOC is " + edcrResponse.get(BPAConstants.SERVICETYPE));

		String nocPath = BPAConstants.NOCTYPE_REQUIRED_MAP
				.replace("{1}", edcrResponse.get(BPAConstants.APPLICATIONTYPE))
				.replace("{2}", edcrResponse.get(BPAConstants.SERVICETYPE))
				.replace("{3}", (StringUtils.isEmpty(bpa.getRiskType()) || !bpa.getRiskType().equalsIgnoreCase("LOW"))
						? "ALL" : bpa.getRiskType().toString());

		List<Object> nocMappingResponse = (List<Object>) JsonPath.read(mdmsData, nocPath);
		List<String> nocTypes = JsonPath.read(nocMappingResponse, "$..type");
		if (!CollectionUtils.isEmpty(nocTypes)) {
			for (String nocType : nocTypes) {
				NocRequest nocRequest = NocRequest.builder()
						.noc(Noc.builder().tenantId(bpa.getTenantId())
								.applicationType(ApplicationType.valueOf(BPAConstants.NOC_APPLICATIONTYPE))
								.sourceRefId(bpa.getApplicationNo()).nocType(nocType).source(BPAConstants.NOC_SOURCE)
								.build())
						.requestInfo(bpaRequest.getRequestInfo()).build();
				createNoc(nocRequest);
			}
		} else {
			log.info("No NOC Mapping has found!!");
		}

	}

	@SuppressWarnings("unchecked")
	private void createNoc(NocRequest nocRequest) {
		StringBuilder uri = new StringBuilder(config.getNocServiceHost());
		uri.append(config.getNocCreateEndpoint());

		LinkedHashMap<String, Object> responseMap = null;
		try {
			log.info("Creating NOC application with nocType : " + nocRequest.getNoc().getNocType());
			responseMap = (LinkedHashMap<String, Object>) serviceRequestRepository.fetchResult(uri, nocRequest);
			NocResponse nocResponse = mapper.convertValue(responseMap, NocResponse.class);
			log.info("NOC created with applicationNo : " + nocResponse.getNoc().get(0).getApplicationNo());
		} catch (Exception se) {
			throw new CustomException("NOC_SERVICE_EXCEPTION",
					" Failed to create NOC of Type " + nocRequest.getNoc().getNocType());
		}
	}

	@SuppressWarnings("unchecked")
	private void updateNoc(NocRequest nocRequest) {
		StringBuilder uri = new StringBuilder(config.getNocServiceHost());
		uri.append(config.getNocUpdateEndpoint());

		LinkedHashMap<String, Object> responseMap = null;
		try {
			responseMap = (LinkedHashMap<String, Object>) serviceRequestRepository.fetchResult(uri, nocRequest);
			NocResponse nocResponse = mapper.convertValue(responseMap, NocResponse.class);
			log.info("NOC updated with applicationNo : " + nocResponse.getNoc().get(0).getApplicationNo());
		} catch (Exception se) {
			throw new CustomException("NOC_SERVICE_EXCEPTION",
					" Failed to update NOC of Type " + nocRequest.getNoc().getNocType());
		}
	}

	@SuppressWarnings("unchecked")
	public List<Noc> fetchNocRecords(BPARequest bpaRequest) {

		StringBuilder url = getNOCWithSourceRef(bpaRequest);

		RequestInfoWrapper requestInfoWrapper = RequestInfoWrapper.builder().requestInfo(bpaRequest.getRequestInfo())
				.build();
		LinkedHashMap<String, Object> responseMap = null;
		try {
			responseMap = (LinkedHashMap<String, Object>) serviceRequestRepository.fetchResult(url, requestInfoWrapper);
			NocResponse nocResponse = mapper.convertValue(responseMap, NocResponse.class);
			return nocResponse.getNoc();
		} catch (Exception e) {
			throw new CustomException("NOC_SERVICE_EXCEPTION", " Unable to fetch the NOC records");
		}
	}

	private StringBuilder getNOCWithSourceRef(BPARequest bpaRequest) {
		StringBuilder uri = new StringBuilder(config.getNocServiceHost());
		uri.append(config.getNocSearchEndpoint());
		uri.append("?tenantId=");
		uri.append(bpaRequest.getBPA().getTenantId());
		NocRequest nocRequest = new NocRequest();
		nocRequest.setRequestInfo(bpaRequest.getRequestInfo());
		uri.append("&sourceRefId=");
		uri.append(bpaRequest.getBPA().getApplicationNo());
		return uri;
	}

	public void manageNocWorkflowAction(BPARequest bpaRequest, Object mdmsData) {
		List<Noc> nocs = fetchNocRecords(bpaRequest);
		initiateNocWorkflow(bpaRequest, mdmsData, nocs);
		approveOfflineNoc(bpaRequest, mdmsData, nocs);
		handleBPARejectedStateForNoc(bpaRequest, nocs);
	}

	@SuppressWarnings("unchecked")
	private void approveOfflineNoc(BPARequest bpaRequest, Object mdmsData, List<Noc> nocs) {
		BPA bpa = bpaRequest.getBPA();

		if (bpa.getStatus().equalsIgnoreCase(BPAConstants.NOCVERIFICATION_STATUS)
				&& bpa.getWorkflow().getAction().equalsIgnoreCase(BPAConstants.ACTION_FORWORD)) {
			List<String> offlneNocs = (List<String>) JsonPath.read(mdmsData, BPAConstants.NOCTYPE_OFFLINE_MAP);			
			if (!CollectionUtils.isEmpty(nocs)) {
				nocs.forEach(noc -> {
					if (offlneNocs.contains(noc.getNocType())) {
						Workflow workflow = Workflow.builder().action(config.getNocAutoApproveAction()).build();
						noc.setWorkflow(workflow);
						NocRequest nocRequest = NocRequest.builder().noc(noc).requestInfo(bpaRequest.getRequestInfo())
								.build();
						updateNoc(nocRequest);
						log.info("Offline NOC auto approved " + noc.getApplicationNo());
					}
				});
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void initiateNocWorkflow(BPARequest bpaRequest, Object mdmsData, List<Noc> nocs) {
		BPA bpa = bpaRequest.getBPA();
		
		Map<String, String> edcrResponse = edcrService.getEDCRDetails(bpaRequest.getRequestInfo(), bpaRequest.getBPA());
		String nocPath = BPAConstants.NOC_TRIGGER_STATE_MAP
				.replace("{1}", edcrResponse.get(BPAConstants.APPLICATIONTYPE))
				.replace("{2}", edcrResponse.get(BPAConstants.SERVICETYPE))
				.replace("{3}", (StringUtils.isEmpty(bpa.getRiskType()) || !bpa.getRiskType().equalsIgnoreCase("LOW"))
						? "ALL" : bpa.getRiskType().toString());
		List<Object> triggerActionStates = (List<Object>) JsonPath.read(mdmsData, nocPath);
		if (!CollectionUtils.isEmpty(triggerActionStates)
				&& triggerActionStates.get(0).toString().equalsIgnoreCase(bpa.getStatus())) {
			if (!CollectionUtils.isEmpty(nocs)) {
				nocs.forEach(noc -> {
					noc.setWorkflow(Workflow.builder().action(config.getNocInitiateAction()).build());
					NocRequest nocRequest = NocRequest.builder().noc(noc).requestInfo(bpaRequest.getRequestInfo())
							.build();
					updateNoc(nocRequest);
					log.info("Noc Initiated : " + noc.getApplicationNo());
				});
			}
		}
	}

	private void handleBPARejectedStateForNoc(BPARequest bpaRequest, List<Noc> nocs) {
		BPA bpa = bpaRequest.getBPA();

		if (bpa.getWorkflow() != null && bpa.getWorkflow().getAction() != null
				&& (bpa.getWorkflow().getAction().equalsIgnoreCase(BPAConstants.ACTION_REJECT))) {
			nocs.forEach(noc -> {
				noc.setWorkflow(Workflow.builder().action(config.getNocVoidAction())
						.comment(bpa.getWorkflow().getComments()).build());
				NocRequest nocRequest = NocRequest.builder().noc(noc).requestInfo(bpaRequest.getRequestInfo()).build();
				updateNoc(nocRequest);
				log.info("Noc Voided : " + noc.getApplicationNo());
			});
		}
	}
}