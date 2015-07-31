/**
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.apache.kerby.kerberos.kerb.server.request;

import org.apache.kerby.kerberos.kerb.KrbCodec;
import org.apache.kerby.kerberos.kerb.KrbConstant;
import org.apache.kerby.kerberos.kerb.KrbErrorCode;
import org.apache.kerby.kerberos.kerb.KrbException;
import org.apache.kerby.kerberos.kerb.common.EncryptionUtil;
import org.apache.kerby.kerberos.kerb.common.KrbUtil;
import org.apache.kerby.kerberos.kerb.crypto.CheckSumHandler;
import org.apache.kerby.kerberos.kerb.crypto.EncryptionHandler;
import org.apache.kerby.kerberos.kerb.crypto.fast.FastUtil;
import org.apache.kerby.kerberos.kerb.identity.KrbIdentity;
import org.apache.kerby.kerberos.kerb.server.KdcContext;
import org.apache.kerby.kerberos.kerb.server.KdcRecoverableException;
import org.apache.kerby.kerberos.kerb.server.preauth.KdcFastContext;
import org.apache.kerby.kerberos.kerb.server.preauth.PreauthContext;
import org.apache.kerby.kerberos.kerb.server.preauth.PreauthHandler;
import org.apache.kerby.kerberos.kerb.spec.ap.ApReq;
import org.apache.kerby.kerberos.kerb.spec.ap.Authenticator;
import org.apache.kerby.kerberos.kerb.spec.base.AuthToken;
import org.apache.kerby.kerberos.kerb.spec.base.CheckSum;
import org.apache.kerby.kerberos.kerb.spec.base.EncryptedData;
import org.apache.kerby.kerberos.kerb.spec.base.EncryptionKey;
import org.apache.kerby.kerberos.kerb.spec.base.EncryptionType;
import org.apache.kerby.kerberos.kerb.spec.base.EtypeInfo;
import org.apache.kerby.kerberos.kerb.spec.base.EtypeInfo2;
import org.apache.kerby.kerberos.kerb.spec.base.EtypeInfo2Entry;
import org.apache.kerby.kerberos.kerb.spec.base.EtypeInfoEntry;
import org.apache.kerby.kerberos.kerb.spec.base.KeyUsage;
import org.apache.kerby.kerberos.kerb.spec.base.KrbError;
import org.apache.kerby.kerberos.kerb.spec.base.KrbMessage;
import org.apache.kerby.kerberos.kerb.spec.base.MethodData;
import org.apache.kerby.kerberos.kerb.spec.base.PrincipalName;
import org.apache.kerby.kerberos.kerb.spec.fast.ArmorType;
import org.apache.kerby.kerberos.kerb.spec.fast.KrbFastArmor;
import org.apache.kerby.kerberos.kerb.spec.fast.KrbFastArmoredReq;
import org.apache.kerby.kerberos.kerb.spec.fast.KrbFastReq;
import org.apache.kerby.kerberos.kerb.spec.kdc.KdcRep;
import org.apache.kerby.kerberos.kerb.spec.kdc.KdcReq;
import org.apache.kerby.kerberos.kerb.spec.pa.PaData;
import org.apache.kerby.kerberos.kerb.spec.pa.PaDataEntry;
import org.apache.kerby.kerberos.kerb.spec.pa.PaDataType;
import org.apache.kerby.kerberos.kerb.spec.ticket.EncTicketPart;
import org.apache.kerby.kerberos.kerb.spec.ticket.Ticket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.List;

public abstract class KdcRequest {

    private static final Logger LOG = LoggerFactory.getLogger(KdcRequest.class);
    private final KdcReq kdcReq;
    private final KdcContext kdcContext;

    private Ticket ticket;
    private boolean isPreAuthenticated;
    private KdcRep reply;
    private InetAddress clientAddress;
    private boolean isTcp = true;
    private EncryptionType encryptionType;
    private EncryptionKey clientKey;
    private KrbIdentity clientEntry;
    private KrbIdentity serverEntry;
    private EncryptionKey serverKey;
    private KrbIdentity tgsEntry;
    private PreauthContext preauthContext;
    private KdcFastContext fastContext;
    private PrincipalName serverPrincipal;
    private byte[] innerBodyout;
    private AuthToken token;
    private Boolean isToken = false;
    private EncryptionKey sessionKey;

    /**
     * Get session key.
     */
    public EncryptionKey getSessionKey() {
        return sessionKey;
    }

    /**
     * Set session key.
     */
    public void setSessionKey(EncryptionKey sessionKey) {
        this.sessionKey = sessionKey;
    }

    public KdcRequest(KdcReq kdcReq, KdcContext kdcContext) {
        this.kdcReq = kdcReq;
        this.kdcContext = kdcContext;

        this.preauthContext = kdcContext.getPreauthHandler()
                .preparePreauthContext(this);
        this.fastContext = new KdcFastContext();
    }

    /**
     * Get kdc context.
     */
    public KdcContext getKdcContext() {
        return kdcContext;
    }

    /**
     * Get KdcReq.
     */
    public KdcReq getKdcReq() {
        return kdcReq;
    }

    /**
     * Get preauth context.
     */
    public PreauthContext getPreauthContext() {
        return preauthContext;
    }

    /**
     * Process the kdcrequest from client and issue the ticket.
     */
    public void process() throws KrbException {
        checkVersion();
        checkTgsEntry();
        kdcFindFast();
        if (PreauthHandler.isToken(getKdcReq().getPaData())) {
            isToken = true;
            preauth();
            checkClient();
            checkServer();
        } else {
            checkClient();
            checkServer();
            preauth();
        }
        authenticate();
        issueTicket();
        makeReply();
    }

    /**
     * Check the tgs entry.
     */
    private void checkTgsEntry() throws KrbException {
        KrbIdentity tgsEntry = getEntry(getTgsPrincipal().getName());
        setTgsEntry(tgsEntry);
    }

    /**
     * Find the fast from padata.
     */
    private void kdcFindFast() throws KrbException {

        PaData paData = getKdcReq().getPaData();
        for (PaDataEntry paEntry : paData.getElements()) {
            if (paEntry.getPaDataType() == PaDataType.FX_FAST) {
                LOG.info("Found fast padata and start to process it.");
                KrbFastArmoredReq fastArmoredReq = KrbCodec.decode(paEntry.getPaDataValue(),
                        KrbFastArmoredReq.class);
                KrbFastArmor fastArmor = fastArmoredReq.getArmor();
                armorApRequest(fastArmor);

                EncryptedData encryptedData = fastArmoredReq.getEncryptedFastReq();
                KrbFastReq fastReq = KrbCodec.decode(
                        EncryptionHandler.decrypt(encryptedData, getArmorKey(), KeyUsage.FAST_ENC),
                        KrbFastReq.class);
                innerBodyout = fastReq.getKdcReqBody().encode();

                // TODO: get checksumed data in stream
                CheckSum checkSum = fastArmoredReq.getReqChecksum();
                if (checkSum == null) {
                    LOG.warn("Checksum is empty.");
                    throw new KrbException(KrbErrorCode.KDC_ERR_PA_CHECKSUM_MUST_BE_INCLUDED);
                }
                CheckSumHandler.verifyWithKey(checkSum, getKdcReq().getReqBody().encode(),
                        getArmorKey().getKeyData(), KeyUsage.FAST_REQ_CHKSUM);
            }
        }
    }

    /**
     * Get the armor key.
     */
    private void armorApRequest(KrbFastArmor fastArmor) throws KrbException {
        if (fastArmor.getArmorType() == ArmorType.ARMOR_AP_REQUEST) {
            ApReq apReq = KrbCodec.decode(fastArmor.getArmorValue(), ApReq.class);

            Ticket ticket = apReq.getTicket();
            EncryptionType encType = ticket.getEncryptedEncPart().getEType();
            EncryptionKey tgsKey = getTgsEntry().getKeys().get(encType);
            if (ticket.getTktvno() != KrbConstant.KRB_V5) {
                throw new KrbException(KrbErrorCode.KRB_AP_ERR_BADVERSION);
            }

            EncTicketPart encPart = EncryptionUtil.unseal(ticket.getEncryptedEncPart(),
                    tgsKey, KeyUsage.KDC_REP_TICKET, EncTicketPart.class);
            ticket.setEncPart(encPart);

            EncryptionKey encKey = ticket.getEncPart().getKey();
            setSessionKey(encKey);

            Authenticator authenticator = EncryptionUtil.unseal(apReq.getEncryptedAuthenticator(),
                    encKey, KeyUsage.AP_REQ_AUTH, Authenticator.class);

            EncryptionKey armorKey = FastUtil.cf2(authenticator.getSubKey(), "subkeyarmor",
                    encKey, "ticketarmor");
            setArmorKey(armorKey);
        }
    }

    /**
     * Get tgs entry.
     */
    public KrbIdentity getTgsEntry() {
        return tgsEntry;
    }

    /**
     * Set tgs entry .
     */
    public void setTgsEntry(KrbIdentity tgsEntry) {
        this.tgsEntry = tgsEntry;
    }

    /**
     * Get whether is tcp.
     */
    public boolean isTcp() {
        return isTcp;
    }

    /**
     * Set use tcp.
     */
    public void isTcp(boolean isTcp) {
        this.isTcp = isTcp;
    }

    /**
     * Get the reply message.
     */
    public KrbMessage getReply() {
        return reply;
    }

    /**
     * Set kdc reply.
     */
    public void setReply(KdcRep reply) {
        this.reply = reply;
    }

    /**
     * Get client address.
     */
    public InetAddress getClientAddress() {
        return clientAddress;
    }

    /**
     * Set client address.
     */
    public void setClientAddress(InetAddress clientAddress) {
        this.clientAddress = clientAddress;
    }

    /**
     * Get encryption type.
     */
    public EncryptionType getEncryptionType() {
        return encryptionType;
    }

    /**
     * Set encryption type.
     */
    public void setEncryptionType(EncryptionType encryptionType) {
        this.encryptionType = encryptionType;
    }

    /**
     * Get ticket.
     */
    public Ticket getTicket() {
        return ticket;
    }

    /**
     * Set ticket.
     */
    public void setTicket(Ticket ticket) {
        this.ticket = ticket;
    }

    /**
     * Get whether pre-authenticated.
     */
    public boolean isPreAuthenticated() {
        return isPreAuthenticated;
    }

    /**
     * Set whether pre-authenticated.
     */
    public void setPreAuthenticated(boolean isPreAuthenticated) {
        this.isPreAuthenticated = isPreAuthenticated;
    }

    /**
     * Get server entry.
     */
    public KrbIdentity getServerEntry() {
        return serverEntry;
    }

    /**
     * Set server entry.
     */
    public void setServerEntry(KrbIdentity serverEntry) {
        this.serverEntry = serverEntry;
    }

    /**
     * Get client entry.
     */
    public KrbIdentity getClientEntry() {
        return clientEntry;
    }

    /**
     * Set client entry.
     */
    public void setClientEntry(KrbIdentity clientEntry) {
        this.clientEntry = clientEntry;
    }

    /**
     * Get client key with entryption type.
     */
    public EncryptionKey getClientKey(EncryptionType encType) throws KrbException {
        return getClientEntry().getKey(encType);
    }

    /**
     * Get client key.
     */
    public EncryptionKey getClientKey() {
        return clientKey;
    }

    /**
     * Set client key.
     */
    public void setClientKey(EncryptionKey clientKey) {
        this.clientKey = clientKey;
    }

    /**
     * Get server key.
     */
    public EncryptionKey getServerKey() {
        return serverKey;
    }

    /**
     * Set server key.
     */
    public void setServerKey(EncryptionKey serverKey) {
        this.serverKey = serverKey;
    }

    /**
     * Get tgs principal name.
     */
    public PrincipalName getTgsPrincipal() {
        PrincipalName result = KrbUtil.makeTgsPrincipal(kdcContext.getKdcRealm());
        return result;
    }

    /**
     * Make reply.
     */
    protected abstract void makeReply() throws KrbException;

    /**
     * Check Version.
     */
    protected void checkVersion() throws KrbException {
        KdcReq request = getKdcReq();

        int kerberosVersion = request.getPvno();
        if (kerberosVersion != KrbConstant.KRB_V5) {
            LOG.warn("Kerberos version: " + kerberosVersion + " should equal to "
                    + KrbConstant.KRB_V5);
            throw new KrbException(KrbErrorCode.KDC_ERR_BAD_PVNO);
        }
    }

    /**
     * Check policy.
     */
    protected void checkPolicy() throws KrbException {
        KrbIdentity entry = getClientEntry();

        // if we can not get the client entry, maybe it is token preauth, ignore it.
        if (entry != null) {
            if (entry.isDisabled()) {
                LOG.warn("Client entry " + entry.getPrincipalName() + " is disabled.");
                throw new KrbException(KrbErrorCode.KDC_ERR_CLIENT_REVOKED);
            }
            if (entry.isLocked()) {
                LOG.warn("Client entry " + entry.getPrincipalName() + " is expired.");
                throw new KrbException(KrbErrorCode.KDC_ERR_CLIENT_REVOKED);
            }
            if (entry.getExpireTime().lessThan(new Date().getTime())) {
                throw new KrbException(KrbErrorCode.KDC_ERR_CLIENT_REVOKED);
            }
        } else {
            LOG.info("Client entry is empty.");
        }
    }

    /**
     * Check client.
     */
    protected abstract void checkClient() throws KrbException;

    /**
     * Do the preatuh.
     */
    protected void preauth() throws KrbException {
        KdcReq request = getKdcReq();

        PaData preAuthData = request.getPaData();

        if (isPreauthRequired()) {
            if (preAuthData == null || preAuthData.isEmpty()) {
                LOG.info("The preauth data is empty.");
                KrbError krbError = makePreAuthenticationError(kdcContext, request,
                        KrbErrorCode.KDC_ERR_PREAUTH_REQUIRED);
                throw new KdcRecoverableException(krbError);
            } else {
                getPreauthHandler().verify(this, preAuthData);
            }
        }

        setPreAuthenticated(true);
    }

    /**
     * Set whether preauth required.
     */
    protected void setPreauthRequired(boolean preauthRequired) {
        preauthContext.setPreauthRequired(preauthRequired);
    }

    /**
     * Get whether preauth required.
     */
    protected boolean isPreauthRequired() {
        return preauthContext.isPreauthRequired();
    }

    /**
     * Get preauth handler.
     */
    protected PreauthHandler getPreauthHandler() {
        return kdcContext.getPreauthHandler();
    }

    /**
     * Check encryption type.
     */
    protected void checkEncryptionType() throws KrbException {
        List<EncryptionType> requestedTypes = getKdcReq().getReqBody().getEtypes();

        EncryptionType bestType = EncryptionUtil.getBestEncryptionType(requestedTypes,
                kdcContext.getConfig().getEncryptionTypes());

        if (bestType == null) {
            LOG.error("Can't get the best encryption type.");
            throw new KrbException(KrbErrorCode.KDC_ERR_ETYPE_NOSUPP);
        }

        setEncryptionType(bestType);
    }

    /**
     * Do some authenticate.
     */
    protected void authenticate() throws KrbException {
        checkEncryptionType();
        checkPolicy();
    }

    /**
     * Issue ticket.
     */
    protected abstract void issueTicket() throws KrbException;

    /**
     * Check server.
     */
    private void checkServer() throws KrbException {
        KdcReq request = getKdcReq();

        PrincipalName principal = request.getReqBody().getSname();
        String serverRealm = request.getReqBody().getRealm();
        if (serverRealm == null || serverRealm.isEmpty()) {
            LOG.info("Can't get the server realm from request, and try to get from kdcContext.");
            serverRealm = kdcContext.getKdcRealm();
        }
        principal.setRealm(serverRealm);

        KrbIdentity serverEntry = getEntry(principal.getName());
        setServerEntry(serverEntry);
        for (EncryptionType encType : request.getReqBody().getEtypes()) {
            if (serverEntry.getKeys().containsKey(encType)) {
                EncryptionKey serverKey = serverEntry.getKeys().get(encType);
                setServerKey(serverKey);
                break;
            }
        }
    }

    /**
     * Make preauthentication error.
     *
     * @param kdcContext
     * @param request
     * @param errorCode
     * @return The krb error reply to client
     */
    protected KrbError makePreAuthenticationError(KdcContext kdcContext, KdcReq request,
                                                  KrbErrorCode errorCode)
            throws KrbException {
        List<EncryptionType> encryptionTypes = kdcContext.getConfig().getEncryptionTypes();
        List<EncryptionType> clientEtypes = request.getReqBody().getEtypes();
        boolean isNewEtype = true;

        EtypeInfo2 eTypeInfo2 = new EtypeInfo2();

        EtypeInfo eTypeInfo = new EtypeInfo();

        for (EncryptionType encryptionType : encryptionTypes) {
            if (clientEtypes.contains(encryptionType)) {
                if (!isNewEtype) {
                    EtypeInfoEntry etypeInfoEntry = new EtypeInfoEntry();
                    etypeInfoEntry.setEtype(encryptionType);
                    etypeInfoEntry.setSalt(null);
                    eTypeInfo.add(etypeInfoEntry);
                }

                EtypeInfo2Entry etypeInfo2Entry = new EtypeInfo2Entry();
                etypeInfo2Entry.setEtype(encryptionType);
                eTypeInfo2.add(etypeInfo2Entry);
            }
        }

        byte[] encTypeInfo = null;
        byte[] encTypeInfo2 = null;
        if (!isNewEtype) {
            encTypeInfo = KrbCodec.encode(eTypeInfo);
        }
        encTypeInfo2 = KrbCodec.encode(eTypeInfo2);

        MethodData methodData = new MethodData();
        //methodData.add(new PaDataEntry(PaDataType.ENC_TIMESTAMP, null));
        if (!isNewEtype) {
            methodData.add(new PaDataEntry(PaDataType.ETYPE_INFO, encTypeInfo));
        }
        methodData.add(new PaDataEntry(PaDataType.ETYPE_INFO2, encTypeInfo2));

        KrbError krbError = new KrbError();
        krbError.setErrorCode(errorCode);
        byte[] encodedData = KrbCodec.encode(methodData);
        krbError.setEdata(encodedData);

        return krbError;
    }

    /**
     * Get identity entry with principal name.
     */
    protected KrbIdentity getEntry(String principal) throws KrbException {
        KrbIdentity entry;
        entry = kdcContext.getIdentityService().getIdentity(principal);

        if (entry == null) {
            // Maybe it is the token preauth, now we ignore check client entry.
            return null;
        }
        return entry;
    }

    /**
     * Get request body.
     */
    protected ByteBuffer getRequestBody() throws KrbException {
        return null;
    }

    /**
     * Get armor key.
     */
    public EncryptionKey getArmorKey() throws KrbException {
        return fastContext.getArmorKey();
    }

    /**
     * Set armor key.
     */
    protected void setArmorKey(EncryptionKey armorKey) {
        fastContext.setArmorKey(armorKey);
    }

    /**
     * Get server principal.
     */
    public PrincipalName getServerPrincipal() {
        return serverPrincipal;
    }

    /**
     * Set server principal.
     */
    public void setServerPrincipal(PrincipalName serverPrincipal) {
        this.serverPrincipal = serverPrincipal;
    }

    /**
     * Get innerbodyout.
     */
    protected byte[] getInnerBodyout() {
        return innerBodyout;
    }

    /**
     * Get whether kdc request with token.
     */
    protected boolean isToken() {
        return isToken;
    }

    /**
     * Set auth token.
     */
    public void setToken(AuthToken authToken) {
        this.token = authToken;
    }

    /**
     * Get auth token.
     */
    protected AuthToken getToken() {
        return token;
    }
}
