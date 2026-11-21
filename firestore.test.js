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

function sampleUserProfile(uid, ts) {
  return {
    userId: uid,
    name: "Amaino Athlete",
    sex: "m",
    age: 25,
    weightKg: 75.0,
    heightCm: 175.0,
    goal: "bulk",
    actMultiplier: 1.55,
    targetKcal: 2800,
    targetProtein: 165,
    targetCarbs: 340,
    targetFat: 80,
    splitId: "ul4",
    equip: "all",
    injury: "none",
    mesoWeek: 1,
    syncNutr: true,
    timeCrunch: false,
    deloadToday: false,
    lang: "ar",
    darkTheme: true,
    waterCups: 4,
    waterDate: "2026-11-21",
    onboarded: true,
    createdAt: ts,
    updatedAt: ts,
  };
}

test("Unauthenticated user cannot read or write any user profile or subcollections", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  const ts = new Date(Date.now() - 1000);

  await assertFails(unauth.collection("users").doc(ALICE_UID).get());
  await assertFails(unauth.collection("users").doc(ALICE_UID).set(sampleUserProfile(ALICE_UID, ts)));
  await assertFails(unauth.collection("users").doc(ALICE_UID).collection("workouts").get());
  await assertFails(unauth.collection("users").doc(ALICE_UID).collection("meals").get());
});

test("Owner can create, update (partial), and read UserProfile; cross-user and ghost fields fail", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).set(sampleUserProfile(ALICE_UID, created))
  );
  await assertSucceeds(alice.collection("users").doc(ALICE_UID).get());

  // Partial update succeeds while preserving createdAt
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).update({
      waterCups: 6,
      injury: "shoulder",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Immutable createdAt mutation fails
  await assertFails(
    alice.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Ghost field injection fails
  await assertFails(
    alice.collection("users").doc(BOB_UID).set({
      ...sampleUserProfile(BOB_UID, created),
      isAdmin: true,
    })
  );

  // Bob cannot read or modify Alice's profile
  await assertFails(bob.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bob.collection("users").doc(ALICE_UID).update({
      waterCups: 1,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Workouts, Meals, BodyLogs, ExerciseBests, CoachClients, CustomExercises enforce owner isolation & schema", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  // Workout create + list
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("workouts").doc("w_1").set({
      userId: ALICE_UID,
      dayName: "Upper A",
      splitId: "ul4",
      dateISO: "2026-11-21",
      completedSets: 14,
      totalVolume: 8450,
      rir: 2,
      pump: "good",
      durationSec: 2700,
      muscles: ["chest", "back"],
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(alice.collection("users").doc(ALICE_UID).collection("workouts").get());
  await assertFails(bob.collection("users").doc(ALICE_UID).collection("workouts").get());

  // Meal create + reject @DocumentId 'id' in payload
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("meals").doc("m_1").set({
      userId: ALICE_UID,
      name: "Chicken Breast & Rice",
      dateISO: "2026-11-21",
      cal: 580,
      protein: 48,
      carbs: 62,
      fat: 12,
      source: "scanner",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(
    alice.collection("users").doc(ALICE_UID).collection("meals").doc("m_2").set({
      id: "m_2",
      userId: ALICE_UID,
      name: "Bad Meal With Id",
      dateISO: "2026-11-21",
      cal: 300,
      protein: 20,
      carbs: 30,
      fat: 10,
      source: "manual",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // BodyLog create + partial update
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("bodyLogs").doc("b_1").set({
      userId: ALICE_UID,
      dateISO: "2026-11-21",
      weightKg: 75.5,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("bodyLogs").doc("b_1").update({
      chestCm: 104.0,
      waistCm: 80.5,
      armCm: 39.0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // ExerciseBest upsert
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("exerciseBests").doc("bench").set({
      userId: ALICE_UID,
      exerciseId: "bench",
      bestWeight: 80.0,
      bestReps: 8,
      e1rm: 101.3,
      suggestOverload: true,
      volumeMod: 1,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // CoachClient create + delete
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("coachClients").doc("c_1").set({
      userId: ALICE_UID,
      name: "Omar",
      targetKcal: 2600,
      splitId: "ul4",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("coachClients").doc("c_1").delete()
  );

  // CustomExercise create
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("customExercises").doc("ce_1").set({
      userId: ALICE_UID,
      splitId: "ul4",
      dayIndex: 0,
      name: "Cable Lateral Raise",
      sets: 4,
      reps: "12-15",
      createdAt: ts,
      updatedAt: ts,
    })
  );
});
