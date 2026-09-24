# Contributing to AEGIS

Three people, one repo, zero merge-conflict drama — as long as everyone
follows this.

## 1. Clone and get your branch

```bash
git clone <the repo URL DJ gives you>
cd aegis
git checkout -b feature/<your-slice>   # feature/ingestion | feature/detection | feature/payout
```

## 2. Only touch your own files

Each package owns its own Java folder, its own template folder, and its
own test folder. See the README.md inside your package
(`src/main/java/com/aegis/<your-package>/README.md`) for the exact list.

The only files shared across all three are `com.aegis.contracts`,
`templates/layout.html`, `static/css/aegis.css`, and `pom.xml` — never
edit these inside a feature branch. If one genuinely needs a change, raise
it with the team first and land it in its own small PR everyone reviews.

## 3. Run it locally

```bash
mvn spring-boot:run
```

Open `http://localhost:8080`. H2 console (to peek at what's actually in
the database) is at `http://localhost:8080/h2-console`
(JDBC URL `jdbc:h2:mem:aegis`, user `sa`, no password).

## 4. Commit and push

Small, frequent commits. Open a PR into `main` as soon as your service
compiles and does something — don't wait until it's "finished."

```bash
git add .
git commit -m "ingestion: mock decision generator + audit log"
git push -u origin feature/<your-slice>
```

Then open a pull request on GitHub into `main`.

## 5. Merging

Since each PR only touches its own folders, merge order barely matters.
If two PRs ever do touch the same shared file, whoever merges second does
a quick manual merge — should be rare.

## 6. Getting a Claude session going on your slice

Use the matching prompt in `docs/CLAUDE_PROMPTS.md` as the very first
message in your own Claude session (one session per person — don't share
one across teammates, it causes overlapping edits).
