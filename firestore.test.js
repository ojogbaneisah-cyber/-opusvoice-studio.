const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validUserAccountPayload(uid) {
  const ts = new Date(Date.now() - 1000);
  return {
    userId: uid,
    displayName: "Alice Vance",
    email: "alice@example.com",
    authProvider: "GOOGLE_EMAIL",
    caiLinkedUsername: "@aria_vance",
    activeCharacterId: "cai_aria_01",
    defaultMuteWindowMs: 950,
    defaultSampleRateHz: 48000,
    defaultMonoChannel: true,
    caiIntegrationEnabled: true,
    whatsappIntegrationEnabled: true,
    telegramIntegrationEnabled: true,
    googleChatIntegrationEnabled: true,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validCharacterProfilePayload(uid, charId) {
  const ts = new Date(Date.now() - 1000);
  return {
    userId: uid,
    characterId: charId,
    name: "Aria Vance",
    handle: "@aria_vance",
    tagline: "Empathetic Late-Night Call Companion",
    personaBio: "Witty, warm, and observant confidante who speaks naturally on live phone calls.",
    greeting: "Hey! I'm on the line—go ahead and put me on speaker, zero chimes enabled.",
    voicePresetId: "Aoede",
    voiceProfileName: "Aoede Velvet (48kHz c.ai Voice)",
    recommendedHoldoffMs: 950,
    createdAt: ts,
    updatedAt: ts,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write user account or character profiles", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(validUserAccountPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("characterProfiles").get()
  );
});

test("Authenticated owner can create, read, and update their own UserAccount document", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(docRef.set(validUserAccountPayload(ALICE_UID)));
  await assertSucceeds(docRef.get());

  await assertSucceeds(
    docRef.update({
      displayName: "Alice Updated",
      defaultMuteWindowMs: 1100,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Cross-user isolation: Bob cannot read or modify Alice's UserAccount or CharacterProfiles", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validUserAccountPayload(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("characterProfiles")
      .doc("cai_aria_01")
      .set(validCharacterProfilePayload(ALICE_UID, "cai_aria_01"))
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("characterProfiles")
      .doc("cai_aria_01")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("characterProfiles")
      .where("userId", "==", ALICE_UID)
      .get()
  );
});

test("Authenticated owner can create and query their own CharacterProfiles with matching userId filter", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const charRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("characterProfiles")
    .doc("cai_aria_01");

  await assertSucceeds(charRef.set(validCharacterProfilePayload(ALICE_UID, "cai_aria_01")));

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("characterProfiles")
      .where("userId", "==", ALICE_UID)
      .get()
  );
});

test("Schema validation: rejects invalid holdoff bounds or shadow fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const badHoldoff = {
    ...validUserAccountPayload(ALICE_UID),
    defaultMuteWindowMs: 9999,
  };
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).set(badHoldoff));

  const ghostFieldPayload = {
    ...validUserAccountPayload(ALICE_UID),
    isAdmin: true,
  };
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).set(ghostFieldPayload));
});
