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

function tsNow() {
  return new Date(Date.now() - 1000);
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

test("Unauthenticated user cannot read or write profile, memories, moods, or stories", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("sync").doc("profile").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("memories").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("mood_entries").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("story_sessions").get());
});

test("Owner can create and update their profile with valid schema, but shadow field is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID).collection("sync").doc("profile");
  const now = tsNow();

  await assertSucceeds(
    profileRef.set({
      userId: ALICE_UID,
      nickname: "Zayn",
      playerLevel: 12,
      levelProgress: 0.65,
      coins: 1500,
      diamonds: 240,
      unlockedCostumes: ["NONE", "HOODIE"],
      unlockedAccessories: ["NONE"],
      unlockedThemes: ["LIVING_ROOM"],
      waterGlassesToday: 2,
      stretchCountToday: 1,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid partial update
  await assertSucceeds(
    profileRef.update({
      coins: 1600,
      updatedAt: tsNow(),
    })
  );

  // Shadow update with ghost field must fail
  await assertFails(
    profileRef.update({
      coins: 9999,
      isAdmin: true,
      updatedAt: tsNow(),
    })
  );
});

test("Cross-user isolation blocks Bob from reading or writing Alice's subcollections", async () => {
  const now = tsNow();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").set({
      userId: ALICE_UID,
      category: "Hobi",
      content: "Suka main basket",
      createdAt: now,
      updatedAt: now,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("memories").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_2").set({
      userId: BOB_UID,
      category: "Hobi",
      content: "Spoof",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Owner can write and query memories, mood entries, and story sessions with valid schema", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = tsNow();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").set({
      userId: ALICE_UID,
      category: "Sekolah",
      content: "Sedang belajar matematika",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("mood_entries").doc("mood_1").set({
      userId: ALICE_UID,
      moodType: "HAPPY",
      note: "Hari ini seru!",
      aiReply: "Mantap! Terus semangat ya!",
      characterId: "zayn",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("story_sessions").doc("story_1").set({
      userId: ALICE_UID,
      themeId: "CRYSTAL_FOREST",
      characterId: "siti",
      currentNarrative: "Kita menemukan pintu cahaya.",
      inventoryLabels: ["Kunci Kristal"],
      historyLog: ["Masuk hutan"],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("memories").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("mood_entries").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("story_sessions").get());
});
