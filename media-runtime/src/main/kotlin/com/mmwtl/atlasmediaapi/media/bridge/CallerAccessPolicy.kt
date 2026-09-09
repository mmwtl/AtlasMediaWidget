package com.mmwtl.atlasmediaapi.media.bridge

/** Security access policy for incoming IPC Messenger callers. */
interface CallerAccessPolicy {
    fun isAllowed(uid: Int, packageNames: Set<String>): Boolean
}

/** Open access policy matching Media Bridge protocol v1 requirements. */
class OpenCallerAccessPolicy : CallerAccessPolicy {
    override fun isAllowed(uid: Int, packageNames: Set<String>): Boolean = true
}
