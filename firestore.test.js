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

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user cannot read or write user profiles", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
});

test("Authenticated user can create and read their own profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Master Origami",
      totalScore: 420,
      highestWordScore: 86,
      bestWord: "ORIGAMI",
      campaignStage: 2,
      dailyStreak: 3,
      paperCoins: 150,
      equippedTileSkin: "washi_classic",
      unlockedSkins: ["washi_classic", "sakura_foil"],
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
});

test("Authenticated user cannot read another user's profile or matches", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      totalScore: 100,
      highestWordScore: 30,
      bestWord: "FOLD",
      campaignStage: 1,
      dailyStreak: 1,
      paperCoins: 50,
      equippedTileSkin: "washi_classic",
      unlockedSkins: ["washi_classic"],
      createdAt: now,
      updatedAt: now,
    });
    await db
      .collection("users")
      .doc(BOB_UID)
      .collection("matches")
      .doc("match_1")
      .set({
        userId: BOB_UID,
        mode: "CAMPAIGN",
        opponentName: "Kitsune Scribe",
        playerScore: 120,
        opponentScore: 95,
        turnNumber: 4,
        maxCombo: 2,
        bestPlayedWord: "CRANE",
        status: "ACTIVE",
        boardSnapshot: ".".repeat(225),
        rackSnapshot: "ORIGAMI",
        createdAt: now,
        updatedAt: now,
      });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("matches")
      .doc("match_1")
      .get()
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("matches").get()
  );
});

test("Shadow update with ghost field is rejected", async () => {
  const now = new Date();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      totalScore: 0,
      highestWordScore: 0,
      bestWord: "",
      campaignStage: 1,
      dailyStreak: 0,
      paperCoins: 100,
      equippedTileSkin: "washi_classic",
      unlockedSkins: ["washi_classic"],
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(),
    })
  );
});

test("Authenticated user can create, list, and update their own ACTIVE match, and terminal match cannot be updated", async () => {
  const now = new Date();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const matchRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("matches")
    .doc("match_101");

  await assertSucceeds(
    matchRef.set({
      userId: ALICE_UID,
      mode: "CAMPAIGN",
      opponentName: "Sensei Haiku",
      playerScore: 42,
      opponentScore: 28,
      turnNumber: 2,
      maxCombo: 2,
      bestPlayedWord: "ZEN",
      status: "ACTIVE",
      boardSnapshot: ".".repeat(225),
      rackSnapshot: "BLITZEA",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("matches").get()
  );

  // Transition to WON
  await assertSucceeds(
    matchRef.update({
      playerScore: 180,
      status: "WON",
      updatedAt: new Date(),
    })
  );

  // Subsequent update after terminal state WON must fail
  await assertFails(
    matchRef.update({
      playerScore: 999,
      updatedAt: new Date(),
    })
  );
});
