/**
 * Licensed to Jasig under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Jasig licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.jasig.portal.events.aggr.login;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.jasig.portal.events.LoginEvent;
import org.jasig.portal.events.PortalEvent;
import org.jasig.portal.events.aggr.AggregationInterval;
import org.jasig.portal.events.aggr.AggregationIntervalHelper;
import org.jasig.portal.events.aggr.AggregationIntervalInfo;
import org.jasig.portal.events.aggr.DateDimension;
import org.jasig.portal.events.aggr.EventAggregationContext;
import org.jasig.portal.events.aggr.IPortalEventAggregator;
import org.jasig.portal.events.aggr.TimeDimension;
import org.jasig.portal.events.aggr.groups.AggregatedGroupMapping;
import org.jasig.portal.events.aggr.session.EventSession;
import org.jasig.portal.jpa.BaseAggrEventsJpaDao.AggrEventsTransactional;
import org.jasig.portal.utils.cache.CacheKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Event aggregator that uses {@link LoginAggregationPrivateDao} to aggregate login events 
 * 
 * @author Eric Dalquist
 * @version $Revision$
 */
public class LoginPortalEventAggregator implements IPortalEventAggregator<LoginEvent> {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    
    private LoginAggregationPrivateDao loginAggregationDao;
    private AggregationIntervalHelper aggregationIntervalHelper;

    @Autowired
    public void setLoginAggregationDao(LoginAggregationPrivateDao loginAggregationDao) {
        this.loginAggregationDao = loginAggregationDao;
    }
    
    @Autowired
    public void setAggregationIntervalHelper(AggregationIntervalHelper aggregationIntervalHelper) {
        this.aggregationIntervalHelper = aggregationIntervalHelper;
    }

    @Override
    public boolean supports(Class<? extends PortalEvent> type) {
        return LoginEvent.class.isAssignableFrom(type);
    }

    @AggrEventsTransactional
    @Override
    public void aggregateEvent(LoginEvent e, EventSession eventSession,
            EventAggregationContext eventAggregationContext,
            Map<AggregationInterval, AggregationIntervalInfo> currentIntervals) {
        
        for (Map.Entry<AggregationInterval, AggregationIntervalInfo> intervalInfoEntry : currentIntervals.entrySet()) {
            final AggregationInterval interval = intervalInfoEntry.getKey();
            final AggregationIntervalInfo intervalInfo = intervalInfoEntry.getValue();
            final DateDimension dateDimension = intervalInfo.getDateDimension();
            final TimeDimension timeDimension = intervalInfo.getTimeDimension();
            
            final Set<AggregatedGroupMapping> groupMappings = new LinkedHashSet<AggregatedGroupMapping>(eventSession.getGroupMappings());
            
            final Set<LoginAggregationImpl> cachedLoginAggregations = getLoginAggregations(eventAggregationContext,
                    interval,
                    dateDimension,
                    timeDimension);
        
            for (final LoginAggregationImpl loginAggregation : cachedLoginAggregations) {
                //Remove the aggregation from the group set to mark that it has been updated
                groupMappings.remove(loginAggregation.getAggregatedGroup());
                updateAggregation(e, intervalInfo, loginAggregation);
            }
            
            //Create any left over groups
            if (!groupMappings.isEmpty()) {
                for (final AggregatedGroupMapping aggregatedGroup : groupMappings) {
                    final LoginAggregationImpl loginAggregation = loginAggregationDao.createLoginAggregation(dateDimension, timeDimension, interval, aggregatedGroup);
                    cachedLoginAggregations.add(loginAggregation);
                    updateAggregation(e, intervalInfo, loginAggregation);
                }
            }
        }
    }

    @AggrEventsTransactional
    @Override
    public void handleIntervalBoundary(AggregationInterval interval, EventAggregationContext eventAggregationContext,
            Map<AggregationInterval, AggregationIntervalInfo> intervals) {
        
        final AggregationIntervalInfo intervalInfo = intervals.get(interval);
        final DateDimension dateDimension = intervalInfo.getDateDimension();
        final TimeDimension timeDimension = intervalInfo.getTimeDimension();
        
        //Complete all of the login aggregations that have been touched by this session
        final Set<LoginAggregationImpl> loginAggregations = this.getLoginAggregations(eventAggregationContext, interval, dateDimension, timeDimension);
        for (final LoginAggregationImpl loginAggregation : loginAggregations) {
            final int duration = intervalInfo.getTotalDuration();
            loginAggregation.intervalComplete(duration);
            logger.debug("Marked complete: " + loginAggregation);
            this.loginAggregationDao.updateLoginAggregation(loginAggregation);
        }
        
        //Look for any uncomplete aggregations from the previous interval
        final AggregationIntervalInfo prevIntervalInfo = this.aggregationIntervalHelper.getIntervalInfo(interval, intervalInfo.getStart().minusMinutes(1));
        final Set<LoginAggregationImpl> unclosedLoginAggregations = this.loginAggregationDao.getUnclosedLoginAggregations(prevIntervalInfo.getStart(), prevIntervalInfo.getEnd(), interval);
        for (final LoginAggregationImpl loginAggregation : unclosedLoginAggregations) {
            final int duration = intervalInfo.getTotalDuration();
            loginAggregation.intervalComplete(duration);
            logger.debug("Marked complete previously missed: " + loginAggregation);
            this.loginAggregationDao.updateLoginAggregation(loginAggregation);
        }
    }

    /**
     * Get the set of existing login aggregations looking first in the session and then in the db
     */
    private Set<LoginAggregationImpl> getLoginAggregations(EventAggregationContext eventAggregationContext,
            final AggregationInterval interval, final DateDimension dateDimension, final TimeDimension timeDimension) {
        
        final CacheKey key = CacheKey.build(this.getClass().getName(), dateDimension.getDate(), timeDimension.getTime(), interval);
        Set<LoginAggregationImpl> cachedLoginAggregations = eventAggregationContext.getAttribute(key);
        if (cachedLoginAggregations == null) {
            //Nothing in the aggr session yet, cache the current set of aggrportalEventAggregationManager.aggregateRawEvents()egations from the DB in the aggr session
            final Set<LoginAggregationImpl> loginAggregations = this.loginAggregationDao.getLoginAggregationsForInterval(dateDimension, timeDimension, interval);
            cachedLoginAggregations = new HashSet<LoginAggregationImpl>(loginAggregations);
            eventAggregationContext.setAttribute(key, cachedLoginAggregations);
        }
        
        return cachedLoginAggregations;
    }

    private void updateAggregation(LoginEvent e, final AggregationIntervalInfo intervalInfo, final LoginAggregationImpl loginAggregation) {
        final String userName = e.getUserName();
        final int duration = intervalInfo.getDurationTo(e.getTimestampAsDate());
        loginAggregation.setDuration(duration);
        loginAggregation.countUser(userName);
    }
}
