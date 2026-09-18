# DOCLIFT
DocLift is a web application that converts a PDF into an editable Microsoft Word
(.docx) document while preserving as much of the original layout as possible: text and reading order,
headings and paragraphs, basic character formatting, and, in its fuller form, images, tables, and
multi-column layouts. 


## Features
- Upload a PDF file (drag-and-drop or file picker) with type and size validation
- Convert to an editable Microsoft Word (.docx) document
- Preserve all visible text in the correct reading order, including multi-column pages
- Detect and reconstruct document structure: headings, paragraphs, and lists
- Carry across basic character formatting: bold, italic, underline, and font size
- Extract images from the PDF and place them at the correct point in the output
- Detect tables and rebuild them as editable Word tables
- A fidelity report/checklist per conversion(enhancement)
- Progress indication while the file uploads and converts



## Running Tests

To run tests, run the following command

```bash
  npm run test
```

## Run Locally

Clone the project

```bash
  git clone https://github.com/AmaliTech-Training-Academy/doclift.git
```

Go to the project directory

```bash
  cd ./apps/web
```

Install dependencies

```bash
  npm install
```

Start the server

```bash
  npm run dev
```
## Tech Stack

**Frontend:** NextJS, TailwindCSS


## Screenshots
<img width="164" height="359" alt="image" src="https://github.com/user-attachments/assets/64b57ef9-8f17-44b4-b1c4-2e237222905b" />
<img width="659" height="476" alt="Screenshot 2026-09-16 141539" src="https://github.com/user-attachments/assets/885c0fb0-06a2-4b27-b3f9-c4e145df1fdf" />
<img width="900" height="539" alt="image" src="https://github.com/user-attachments/assets/7477ff20-b6f0-4a13-920c-062cab18216d" />




