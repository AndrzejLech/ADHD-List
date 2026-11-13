import { after, before, beforeEach, test } from 'node:test';
import { readFileSync } from 'node:fs';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  writeBatch,
  arrayRemove, arrayUnion, collection, deleteDoc, doc, getDoc, getDocs, query, setDoc, updateDoc, where,
} from 'firebase/firestore';

let env;
const LIST = 'AbCdEfGhIjKlMnOpQrSt';

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-adhdlist',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});
after(() => env.cleanup());
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const admin = ctx.firestore();
    await setDoc(doc(admin, 'lists', LIST), { name: 'Groceries', members: ['alice'] });
    await setDoc(doc(admin, 'lists', LIST, 'tasks', 't1'), { message: 'milk', state: false, position: 0 });
  });
});

// One Firestore instance per user: a context's firestore() can only be set up once.
const dbs = new Map();
const db = (uid) => {
  if (!dbs.has(uid)) dbs.set(uid, (uid ? env.authenticatedContext(uid) : env.unauthenticatedContext()).firestore());
  return dbs.get(uid);
};
const list = (uid) => doc(db(uid), 'lists', LIST);
const task = (uid, id = 't1') => doc(db(uid), 'lists', LIST, 'tasks', id);

test('signed-out users can do nothing', async () => {
  await assertFails(getDoc(list(null)));
  await assertFails(setDoc(doc(db(null), 'lists', 'x'), { name: 'n', members: [] }));
});

test('creating a list: only with yourself as the sole member', async () => {
  await assertSucceeds(setDoc(doc(db('bob'), 'lists', 'b1'), { name: 'Mine', members: ['bob'] }));
  await assertFails(setDoc(doc(db('bob'), 'lists', 'b2'), { name: 'Sneaky', members: ['bob', 'alice'] }));
  await assertFails(setDoc(doc(db('bob'), 'lists', 'b3'), { name: 'Extra', members: ['bob'], admin: true }));
});

test('queries only return your own lists', async () => {
  await assertSucceeds(getDocs(query(collection(db('alice'), 'lists'), where('members', 'array-contains', 'alice'))));
  await assertFails(getDocs(collection(db('bob'), 'lists')));
});

test('with the code, a stranger can join (add only themselves)', async () => {
  await assertSucceeds(getDoc(list('bob')));
  await assertFails(updateDoc(list('bob'), { members: arrayUnion('bob', 'carol') }));
  await assertFails(updateDoc(list('bob'), { members: ['bob'] })); // would remove alice
  await assertFails(updateDoc(list('bob'), { name: 'Hijacked', members: arrayUnion('bob') }));
  await assertSucceeds(updateDoc(list('bob'), { members: arrayUnion('bob') }));
});

test('non-members cannot touch tasks', async () => {
  await assertFails(getDoc(task('bob')));
  await assertFails(updateDoc(task('bob'), { state: true }));
  await assertFails(setDoc(task('bob', 't2'), { message: 'x', state: false, position: 1 }));
  await assertFails(deleteDoc(task('bob')));
});

test('members read and edit tasks; tasks must have the right shape', async () => {
  await assertSucceeds(getDoc(task('alice')));
  await assertSucceeds(updateDoc(task('alice'), { state: true }));
  await assertSucceeds(setDoc(task('alice', 't2'), { message: 'eggs', state: false, position: 1 }));
  await assertFails(setDoc(task('alice', 't3'), { message: 'eggs', state: 'yes', position: 1 }));
  await assertFails(setDoc(task('alice', 't4'), { message: 'x'.repeat(1001), state: false, position: 1 }));
  await assertSucceeds(deleteDoc(task('alice', 't2')));
});

test('members rename; a member can leave but not remove others; nobody deletes the list', async () => {
  await assertSucceeds(updateDoc(list('alice'), { name: 'Shopping' }));
  await assertFails(updateDoc(list('bob'), { name: 'Not a member' }));
  await env.withSecurityRulesDisabled((ctx) => updateDoc(doc(ctx.firestore(), 'lists', LIST), { members: ['alice', 'bob'] }));
  await assertFails(updateDoc(list('bob'), { members: arrayRemove('alice') }));
  await assertSucceeds(updateDoc(list('bob'), { members: arrayRemove('bob') }));
  await assertFails(deleteDoc(list('alice')));
});

test('sharing: the list and its tasks can be created in one batch', async () => {
  const batch = writeBatch(db('dave'));
  batch.set(doc(db('dave'), 'lists', 'd1'), { name: 'Shared', members: ['dave'] });
  batch.set(doc(db('dave'), 'lists', 'd1', 'tasks', 'x'), { message: 'eggs', state: false, position: 0 });
  await assertSucceeds(batch.commit());
});

test('a stranger cannot sneak tasks in alongside a fake join', async () => {
  const batch = writeBatch(db('eve'));
  batch.update(list('eve'), { members: arrayUnion('eve', 'mallory') });
  batch.set(task('eve', 'evil'), { message: 'spam', state: false, position: 9 });
  await assertFails(batch.commit());
});
