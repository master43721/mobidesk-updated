const functions = require("firebase-functions");
const admin = require("firebase-admin");
const { ComputeManagementClient } = require("@azure/arm-compute");
const { DefaultAzureCredential } = require("@azure/identity");
const crypto = require("crypto");

admin.initializeApp();

// Azure Configuration (Set these via firebase functions:config:set azure.id="..." etc)
const AZURE_SUBSCRIPTION_ID = functions.config().azure.subscription_id;
const RESOURCE_GROUP = "MobiLab_Resource_Group";
const VM_NAME = "MobiLab_Windows_Server";

exports.requestRemoteSession = functions.https.onCall(async (data, context) => {
    // 1. Verify Authentication
    if (!context.auth) {
        throw new functions.https.HttpsError("unauthenticated", "User must be logged in.");
    }

    const db = admin.firestore();
    const profilesRef = db.collection("vm_profiles");

    try {
        // 2. Check for an available profile (user1 to user8)
        const availableProfiles = await profilesRef.where("status", "==", "available").limit(1).get();

        if (!availableProfiles.empty) {
            const profileDoc = availableProfiles.docs[0];
            await profileDoc.ref.update({ status: "in-use", last_user: context.auth.uid });
            return {
                success: true,
                username: profileDoc.data().username,
                ip: profileDoc.data().server_ip
            };
        }

        // 3. Logic for "All profiles in-use" -> Create New User via Azure Run Command
        console.log("All profiles occupied. Triggering new user creation on Azure VM...");

        const nextUserNumber = (await profilesRef.count().get()).data().count + 1;
        const newUsername = `user${nextUserNumber}`;
        const newPassword = crypto.randomBytes(12).toString("hex") + "Aa1!"; // Complex password

        // Initialize Azure Client
        const credential = new DefaultAzureCredential();
        const client = new ComputeManagementClient(credential, AZURE_SUBSCRIPTION_ID);

        // PowerShell Script to execute on VM
        const psScript = [
            `$password = ConvertTo-SecureString "${newPassword}" -AsPlainText -Force`,
            `New-LocalUser -Name "${newUsername}" -Password $password`,
            `Add-LocalGroupMember -Group "Remote Desktop Users" -Member "${newUsername}"`
        ];

        // Trigger Azure Run Command
        await client.virtualMachines.beginRunCommandAndWait(RESOURCE_GROUP, VM_NAME, {
            commandId: "RunPowerShellScript",
            script: psScript
        });

        // 4. Update Database with the new credentials
        const newProfileData = {
            username: newUsername,
            password: newPassword, // Store securely or return once
            status: "in-use",
            last_user: context.auth.uid,
            server_ip: "135.119.92.61", // Your Static Azure IP
            created_at: admin.firestore.FieldValue.serverTimestamp()
        };

        await profilesRef.doc(newUsername).set(newProfileData);

        return {
            success: true,
            username: newUsername,
            password: newPassword, // Note: Handled once during creation
            ip: newProfileData.server_ip
        };

    } catch (error) {
        console.error("Session Request Failed:", error);
        throw new functions.https.HttpsError("internal", error.message);
    }
});
