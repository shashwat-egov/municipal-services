package org.egov.land.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.validation.Valid;

import org.egov.bpa.web.model.user.UserDetailResponse;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.Role;
import org.egov.land.repository.LandRepository;
import org.egov.land.validator.LandValidator;
import org.egov.land.web.models.LandInfo;
import org.egov.land.web.models.LandRequest;
import org.egov.land.web.models.LandSearchCriteria;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class LandService {

	@Autowired
	LandValidator landValidator;

	@Autowired
	private LandEnrichmentService enrichmentService;

	@Autowired
	private LandUserService userService;

	@Autowired
	private LandRepository repository;

	public LandInfo create(@Valid LandRequest landRequest) {
		if (landRequest.getLandInfo().getTenantId().split("\\.").length == 1) {
			throw new CustomException(" Invalid Tenant ", " Application cannot be create at StateLevel");
		}
		
		landValidator.validateLandInfo(landRequest);
		userService.manageUser(landRequest);
		
		enrichmentService.enrichLandInfoRequest(landRequest, false);		
		repository.save(landRequest);
		return landRequest.getLandInfo();
	}

	public LandInfo update(@Valid LandRequest landRequest) {
		LandInfo landInfo = landRequest.getLandInfo();

		if (landInfo.getId() == null) {
			throw new CustomException("UPDATE ERROR", "Id is mandatory to update ");
		}

		landInfo.getOwners().forEach(owner -> {
			if (owner.getOwnerType() == null) {
				owner.setOwnerType("NONE");
			}
		});
		landValidator.validateLandInfo(landRequest);
		userService.manageUser(landRequest);
		enrichmentService.enrichLandInfoRequest(landRequest, true);
		repository.update(landRequest);

		return landRequest.getLandInfo();
	}
	
	public List<LandInfo> search(LandSearchCriteria criteria, RequestInfo requestInfo) {
		List<LandInfo> landInfo;
		landValidator.validateSearch(requestInfo, criteria);
		if (criteria.getMobileNumber() != null) {
			landInfo = getLandFromMobileNumber(criteria, requestInfo);
		} else {
			List<String> roles = new ArrayList<>();
			for (Role role : requestInfo.getUserInfo().getRoles()) {
				roles.add(role.getCode());
			}
			
			landInfo = getLandWithOwnerInfo(criteria, requestInfo);
		}
		return landInfo;
	}
	
	private List<LandInfo> getLandFromMobileNumber(LandSearchCriteria criteria, RequestInfo requestInfo) {

		List<LandInfo> landInfo = new LinkedList<>();
		UserDetailResponse userDetailResponse = userService.getUser(criteria, requestInfo);
		// If user not found with given user fields return empty list
		if (userDetailResponse.getUser().size() == 0) {
			return Collections.emptyList();
		}

		landInfo = repository.getLandInfoData(criteria);

		if (landInfo.size() == 0) {
			return Collections.emptyList();
		}
		enrichmentService.enrichLandInfoSearch(landInfo, criteria, requestInfo);
		return landInfo;
	}
	

	/**
	 * Returns the landInfo with enriched owners from user service
	 * 
	 * @param criteria
	 *            The object containing the parameters on which to search
	 * @param requestInfo
	 *            The search request's requestInfo
	 * @return List of landInfo for the given criteria
	 */
	public List<LandInfo> getLandWithOwnerInfo(LandSearchCriteria criteria, RequestInfo requestInfo) {
		List<LandInfo> landInfos = repository.getLandInfoData(criteria);
		log.info("Owners after repository call", landInfos.get(0).getOwners().toString());
		if (landInfos.isEmpty())
			return Collections.emptyList();
		landInfos = enrichmentService.enrichLandInfoSearch(landInfos, criteria, requestInfo);
		log.info("final call", landInfos.get(0).getOwners().toString());
		return landInfos;
	}
}
