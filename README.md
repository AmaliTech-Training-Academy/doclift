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

## Environment Variables






