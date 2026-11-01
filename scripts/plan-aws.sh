#!/bin/sh
# Creates a reviewable plan only. No apply or secret-value reads.
set -eu
umask 077
: "${TRAVBOOK_AWS_PROFILE:?Set the approved AWS profile}"
: "${TF_VAR_account_id:?Set the approved AWS account ID}"
: "${TF_VAR_region:?Set the approved region}"
: "${TRAVBOOK_TF_BACKEND:?Set the absolute backend configuration path}"
: "${TRAVBOOK_TF_VARS:?Set the absolute non-secret tfvars path}"
: "${TRAVBOOK_TF_PLAN:?Set the absolute private output plan path}"
case "$TRAVBOOK_TF_BACKEND:$TRAVBOOK_TF_VARS:$TRAVBOOK_TF_PLAN" in /*:/*:/*) ;; *) echo 'Configuration and output paths must be absolute.' >&2; exit 2;; esac
actual_account=$(aws --profile "$TRAVBOOK_AWS_PROFILE" --region "$TF_VAR_region" sts get-caller-identity --query Account --output text)
if [ "$actual_account" != "$TF_VAR_account_id" ]; then echo 'AWS account mismatch; refusing to plan.' >&2; exit 2; fi
export AWS_PROFILE="$TRAVBOOK_AWS_PROFILE"
terraform -chdir=infra/aws init -input=false -backend-config="$TRAVBOOK_TF_BACKEND"
terraform -chdir=infra/aws plan -input=false -var-file="$TRAVBOOK_TF_VARS" -var="aws_profile=$TRAVBOOK_AWS_PROFILE" -var="account_id=$TF_VAR_account_id" -var="region=$TF_VAR_region" -out="$TRAVBOOK_TF_PLAN"
